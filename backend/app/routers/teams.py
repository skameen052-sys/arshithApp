from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel

from app.services.firestore_service import firestore_service
from app.dependencies.auth import get_current_user, get_current_admin

router = APIRouter(prefix="/teams", tags=["Teams"])


class TeamCreate(BaseModel):
    teamName: str
    department: Optional[str] = "Operations"
    description: Optional[str] = ""
    leader: Optional[str] = None
    members: Optional[List[str]] = []


def format_team(doc: dict) -> dict:
    members = doc.get("members", [])
    if not isinstance(members, list):
        members = []
    leader = doc.get("leader") or (members[0] if members else "Unassigned")
    return {
        "id": doc.get("id"),
        "name": doc.get("name", doc.get("teamName", "")),
        "teamName": doc.get("name", doc.get("teamName", "")),
        "department": doc.get("department", "Operations"),
        "description": doc.get("description", ""),
        "leader": leader,
        "members": members,
        "memberCount": len(members),
    }


format_team_response = format_team


@router.get("", response_model=List[dict])
def list_teams(
    current_user=Depends(get_current_user),
):
    """List operational teams and member lists from Firestore."""
    docs = firestore_service.list_documents("teams")
    docs.sort(key=lambda t: str(t.get("name", "")).lower())
    return [format_team(t) for t in docs]


@router.post("", status_code=status.HTTP_201_CREATED, response_model=dict)
def create_team(
    data: TeamCreate,
    admin_user=Depends(get_current_admin),
):
    """Create team and enroll staff members (Admin only)."""
    clean_name = data.teamName.strip()
    if not clean_name:
        raise HTTPException(status_code=400, detail="Team name is required.")

    # Check duplicate
    existing = firestore_service.list_documents("teams", [("name", "==", clean_name)])
    if existing:
        raise HTTPException(status_code=409, detail=f"Team '{clean_name}' already exists.")

    # Resolve member names if needed from employees
    all_emps = firestore_service.list_documents("employees")
    emp_map = {}
    for e in all_emps:
        emp_map[str(e.get("employee_id", "")).strip().lower()] = e.get("full_name")
        emp_map[str(e.get("full_name", "")).strip().lower()] = e.get("full_name")

    resolved_members = []
    for m in (data.members or []):
        m_clean = m.strip()
        if not m_clean:
            continue
        resolved_name = emp_map.get(m_clean.lower(), m_clean)
        if resolved_name not in resolved_members:
            resolved_members.append(resolved_name)

    now_iso = datetime.now(timezone.utc).isoformat()
    team_data = {
        "name": clean_name,
        "teamName": clean_name,
        "department": data.department or "Operations",
        "description": data.description.strip() if data.description else "",
        "leader": data.leader,
        "members": resolved_members,
        "created_at": now_iso,
        "updated_at": now_iso,
    }

    doc_id = firestore_service.create_document("teams", team_data)
    team_data["id"] = doc_id

    # Record audit log
    firestore_service.create_document("audit_logs", {
        "actor_id": admin_user.id,
        "actor_email": admin_user.email,
        "action": "CREATE_TEAM",
        "target_type": "teams",
        "target_id": doc_id,
        "timestamp": now_iso,
    })

    return format_team(team_data)


@router.delete("/{team_id}", status_code=status.HTTP_200_OK)
def delete_team(
    team_id: str,
    admin_user=Depends(get_current_admin),
):
    """Disband team (Admin only)."""
    team = firestore_service.get_document("teams", str(team_id))
    if not team:
        # Check integer match
        teams = firestore_service.list_documents("teams")
        for t in teams:
            if str(t.get("id")) == str(team_id):
                team = t
                break

    if not team:
        raise HTTPException(status_code=404, detail="Team not found")

    firestore_service.delete_document("teams", str(team["id"]))

    # Record audit log
    firestore_service.create_document("audit_logs", {
        "actor_id": admin_user.id,
        "actor_email": admin_user.email,
        "action": "DELETE_TEAM",
        "target_type": "teams",
        "target_id": str(team["id"]),
        "timestamp": datetime.now(timezone.utc).isoformat(),
    })

    return {"success": True, "message": "Team deleted successfully"}
