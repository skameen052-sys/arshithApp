import json
import logging
import os
import uuid
import asyncio

from datetime import datetime, timezone
from typing import List, Optional, Dict, Any, Set

from fastapi import (
    APIRouter,
    Depends,
    HTTPException,
    status,
    Query,
    WebSocket,
    WebSocketDisconnect,
    UploadFile,
    File,
)
from pydantic import BaseModel

from app.services.firestore_service import (
    firestore_service,
    FirestoreService,
)
from app.dependencies.auth import get_current_user
from app.core.security import decode_access_token
from app.core.firebase import verify_firebase_token


logger = logging.getLogger("uvicorn.error")

router = APIRouter(prefix="/chat", tags=["Teams Chat"])

UPLOAD_DIR = os.path.join(
    os.path.dirname(os.path.dirname(__file__)),
    "static",
    "chat_uploads",
)

os.makedirs(UPLOAD_DIR, exist_ok=True)

ALLOWED_EXTENSIONS = {
    ".png", ".jpg", ".jpeg", ".gif", ".webp",
    ".pdf", ".docx", ".doc", ".xlsx", ".xls",
    ".txt", ".csv", ".zip",
    ".webm", ".mp3", ".wav", ".ogg", ".m4a", ".aac",
}

MAX_FILE_SIZE = 10 * 1024 * 1024


# =============================================================================
# WEBSOCKET CONNECTION MANAGER
# =============================================================================

class ConnectionManager:

    def __init__(self):
        self.active_connections: Dict[str, Set[WebSocket]] = {}
        self.online_users: Set[str] = set()

    def _normalize_key(self, key: Any) -> str:
        return str(key).strip().lower() if key else ""

    async def connect(
        self,
        websocket: WebSocket,
        user_keys: List[str],
    ):
        await websocket.accept()

        for key in user_keys:
            normalized = self._normalize_key(key)

            if not normalized:
                continue

            self.active_connections.setdefault(normalized, set())
            self.active_connections[normalized].add(websocket)
            self.online_users.add(normalized)

    def disconnect(
        self,
        websocket: WebSocket,
        user_keys: List[str],
    ):

        for key in user_keys:
            normalized = self._normalize_key(key)

            if normalized in self.active_connections:

                self.active_connections[normalized].discard(websocket)

                if not self.active_connections[normalized]:
                    del self.active_connections[normalized]
                    self.online_users.discard(normalized)

    def is_user_online(self, user_key: Any) -> bool:

        normalized = self._normalize_key(user_key)

        return (
            normalized in self.online_users
            and bool(self.active_connections.get(normalized))
        )

    async def broadcast_to_keys(
        self,
        user_keys: List[str],
        event: dict,
    ):

        sent_sockets: Set[WebSocket] = set()

        for key in user_keys:

            normalized = self._normalize_key(key)

            if normalized not in self.active_connections:
                continue

            for websocket in list(self.active_connections[normalized]):

                if websocket in sent_sockets:
                    continue

                try:
                    await websocket.send_text(json.dumps(event))
                    sent_sockets.add(websocket)

                except Exception as exc:
                    logger.debug(
                        "WebSocket send failed: %s",
                        exc,
                    )

    async def broadcast_all(self, event: dict):

        sent_sockets: Set[WebSocket] = set()

        for sockets in self.active_connections.values():

            for websocket in list(sockets):

                if websocket in sent_sockets:
                    continue

                try:
                    await websocket.send_text(json.dumps(event))
                    sent_sockets.add(websocket)

                except Exception:
                    pass


manager = ConnectionManager()


# =============================================================================
# USER IDENTITY HELPERS
# =============================================================================

def get_user_identifiers(user: Any) -> Dict[str, str]:

    role = str(
        getattr(user, "role", "employee") or "employee"
    ).strip().lower()

    email = str(
        getattr(user, "email", "") or ""
    ).strip().lower()

    emp_id = str(
        getattr(user, "employee_id", None)
        or getattr(user, "employeeId", None)
        or ""
    ).strip()

    uid = str(
        getattr(user, "id", None)
        or getattr(user, "uid", None)
        or ""
    ).strip()

    name = (
        getattr(user, "full_name", None)
        or getattr(user, "fullName", None)
        or getattr(user, "name", None)
        or (email.split("@")[0].title() if email else "User")
    )

    primary_id = emp_id or uid or email

    if role == "admin" and not emp_id:
        primary_id = "ADM-001"

    return {
        "primary_id": primary_id,
        "employee_id": emp_id,
        "uid": uid,
        "email": email,
        "role": role,
        "name": str(name),
    }


def get_all_user_keys(identifiers: Dict[str, str]) -> List[str]:

    keys = []

    for key in [
        "primary_id",
        "employee_id",
        "uid",
        "email",
        "name",
    ]:

        value = identifiers.get(key)

        if value and str(value).strip():
            keys.append(str(value).strip().lower())

    if identifiers.get("role") == "admin":

        keys.extend([
            "admin",
            "adm-001",
            "administrator",
            "admin@arshith.com",
        ])

    return list(set(keys))


def user_matches_target(
    identifiers: Dict[str, str],
    target: Any,
) -> bool:

    if not target:
        return False

    target_norm = str(target).strip().lower()

    return target_norm in set(get_all_user_keys(identifiers))


def _normalize_member_keys(value: Any) -> Set[str]:
    """
    Convert Firestore membership values into normalized identifiers.

    Supports:
    - String employee IDs
    - Firebase UIDs
    - Email addresses
    - Lists
    - Dictionary member objects
    """

    if value is None:
        return set()

    if isinstance(value, dict):

        values = [
            value.get("uid"),
            value.get("user_id"),
            value.get("userId"),
            value.get("employee_id"),
            value.get("employeeId"),
            value.get("email"),
            value.get("id"),
            value.get("name"),
        ]

    elif isinstance(value, list):
        values = value

    else:
        values = [value]

    result = set()

    for item in values:

        if item is None:
            continue

        if isinstance(item, dict):
            result.update(_normalize_member_keys(item))
            continue

        normalized = str(item).strip().lower()

        if normalized:
            result.add(normalized)

    return result


def get_team_member_keys(team: Dict[str, Any]) -> Set[str]:
    """
    Supports both Firestore membership field names:
    members and memberIds.
    """

    result = set()

    result.update(
        _normalize_member_keys(team.get("members", []))
    )

    result.update(
        _normalize_member_keys(team.get("memberIds", []))
    )

    return result


def is_general_team(team: Dict[str, Any]) -> bool:

    name = str(
        team.get("name")
        or team.get("teamName")
        or ""
    ).strip().lower()

    return (
        "general" in name
        or "all company" in name
    )


def resolve_canonical_user_id(identifier: Any) -> str:

    if not identifier:
        return ""

    clean = str(identifier).strip()
    normalized = clean.lower()

    admin_aliases = {
        "admin",
        "adm-001",
        "administrator",
        "admin@arshith.com",
        "system_admin",
    }

    if normalized in admin_aliases:
        return "ADM-001"

    try:

        employees = firestore_service.list_documents("employees")

        for employee in employees:

            employee_id = str(
                employee.get("employee_id")
                or employee.get("employeeId")
                or ""
            ).strip()

            document_id = str(
                employee.get("id") or ""
            ).strip()

            email = str(
                employee.get("email") or ""
            ).strip().lower()

            name = str(
                employee.get("full_name")
                or employee.get("name")
                or ""
            ).strip().lower()

            uid = str(
                employee.get("user_id")
                or employee.get("uid")
                or ""
            ).strip().lower()

            possible_ids = {
                employee_id.lower(),
                document_id.lower(),
                email,
                name,
                uid,
            }

            if normalized in possible_ids:
                return (
                    employee_id.upper()
                    if employee_id
                    else clean.upper()
                )

    except Exception as exc:

        logger.debug(
            "Error resolving canonical user ID %s: %s",
            identifier,
            exc,
        )

    return clean.upper()


def get_user_team_keys(
    identifiers: Dict[str, str],
) -> Set[str]:

    keys = set(get_all_user_keys(identifiers))

    canonical_id = resolve_canonical_user_id(
        identifiers.get("primary_id")
    )

    if canonical_id:
        keys.add(canonical_id.lower())

    return keys


def is_user_team_member(
    team: Dict[str, Any],
    identifiers: Dict[str, str],
) -> bool:

    if identifiers.get("role") == "admin":
        return True

    if is_general_team(team):
        return True

    user_keys = get_user_team_keys(identifiers)
    team_keys = get_team_member_keys(team)

    return bool(user_keys.intersection(team_keys))


def resolve_employee_profile(
    identifier: Any,
) -> Optional[Dict[str, Any]]:

    if not identifier:
        return None

    clean = str(identifier).strip()
    normalized = clean.lower()

    if normalized in {
        "admin",
        "adm-001",
        "administrator",
        "admin@arshith.com",
        "system_admin",
    }:

        return {
            "name": "Administrator",
            "employee_id": "ADM-001",
            "role": "admin",
            "profile_photo": None,
            "photo_url": None,
            "department": "Administration",
            "designation": "Executive Administration",
        }

    try:

        employees = firestore_service.list_documents("employees")

        for employee in employees:

            employee_id = str(
                employee.get("employee_id")
                or employee.get("employeeId")
                or ""
            ).strip().lower()

            document_id = str(
                employee.get("id") or ""
            ).strip().lower()

            email = str(
                employee.get("email") or ""
            ).strip().lower()

            name = str(
                employee.get("full_name")
                or employee.get("name")
                or ""
            ).strip().lower()

            uid = str(
                employee.get("user_id")
                or employee.get("uid")
                or ""
            ).strip().lower()

            if normalized in {
                employee_id,
                document_id,
                email,
                name,
                uid,
            }:

                real_name = (
                    employee.get("full_name")
                    or employee.get("name")
                    or clean
                )

                photo = (
                    employee.get("profile_photo")
                    or employee.get("profilePhoto")
                    or employee.get("photoUrl")
                    or employee.get("photo_url")
                    or employee.get("photo")
                )

                return {
                    "name": real_name,
                    "employee_id": (
                        employee.get("employee_id")
                        or employee.get("employeeId")
                        or clean.upper()
                    ),
                    "role": "employee",
                    "profile_photo": photo,
                    "photo_url": photo,
                    "department": employee.get(
                        "department",
                        "Operations",
                    ),
                    "designation": employee.get(
                        "designation",
                        "Staff Member",
                    ),
                }

    except Exception as exc:

        logger.debug(
            "Error looking up employee profile for %s: %s",
            identifier,
            exc,
        )

    return None


def notify_profile_updated(
    user_id: str,
    emp_id: Optional[str] = None,
    photo_url: Optional[str] = None,
    name: Optional[str] = None,
):

    payload = {
        "type": "user_profile_updated",
        "user_id": user_id,
        "employee_id": emp_id or user_id,
        "name": name,
        "profile_photo": photo_url,
        "photo_url": photo_url,
    }

    try:

        loop = asyncio.get_running_loop()

        loop.create_task(
            manager.broadcast_all(payload)
        )

    except RuntimeError:
        logger.debug(
            "No running event loop for profile notification."
        )

    except Exception as exc:
        logger.debug(
            "Could not broadcast profile update: %s",
            exc,
        )


def build_direct_conversation_id(
    user_a: Any,
    user_b: Any,
) -> str:

    first = resolve_canonical_user_id(user_a)
    second = resolve_canonical_user_id(user_b)

    pair = sorted([first, second])

    return f"dm_{pair[0]}_{pair[1]}"


def get_message_conversation_id(
    message: Dict[str, Any],
) -> str:

    conversation_id = message.get("conversation_id")

    if conversation_id:
        return str(conversation_id).strip()

    chat_type = message.get("chat_type", "direct")

    if chat_type == "team":

        team_id = str(
            message.get("team_id")
            or message.get("target_id")
            or ""
        ).strip()

        return f"team_{team_id}"

    sender_id = message.get("sender_id", "")

    receiver_id = (
        message.get("receiver_id")
        or message.get("target_id")
        or ""
    )

    return build_direct_conversation_id(
        sender_id,
        receiver_id,
    )


def migrate_legacy_chat_messages():

    try:

        messages = firestore_service.list_documents(
            "chat_messages"
        )

        for message in messages:

            message_id = str(message.get("id") or "")

            if not message_id:
                continue

            updates = {}

            chat_type = message.get("chat_type", "direct")

            if not message.get("conversation_id"):

                updates["conversation_id"] = (
                    get_message_conversation_id(message)
                )

            if not message.get("participant_ids"):

                if chat_type == "team":

                    team_id = str(
                        message.get("team_id")
                        or message.get("target_id")
                        or ""
                    ).strip()

                    updates["participant_ids"] = [team_id]

                else:

                    sender = resolve_canonical_user_id(
                        message.get("sender_id", "")
                    )

                    receiver = resolve_canonical_user_id(
                        message.get("receiver_id")
                        or message.get("target_id", "")
                    )

                    updates["participant_ids"] = sorted([
                        sender,
                        receiver,
                    ])

            if updates:

                firestore_service.update_document(
                    "chat_messages",
                    message_id,
                    updates,
                )

    except Exception as exc:

        logger.warning(
            "Error during legacy chat migration: %s",
            exc,
        )


def get_team_by_id_or_name(target: str):

    target_normalized = str(target).strip().lower()

    teams = firestore_service.list_documents("teams")

    for team in teams:

        team_id = str(team.get("id") or "").strip().lower()

        team_name = str(
            team.get("name")
            or team.get("teamName")
            or ""
        ).strip().lower()

        if target_normalized in {team_id, team_name}:
            return team

    return None


def get_team_websocket_keys(
    team: Dict[str, Any],
) -> List[str]:

    keys = set(get_team_member_keys(team))

    for member in list(keys):

        canonical = resolve_canonical_user_id(member)

        if canonical:
            keys.add(canonical.lower())

    return list(keys)


def validate_conversation_access(
    conversation_id: str,
    identifiers: Dict[str, str],
):

    if conversation_id.startswith("team_"):

        team_id = conversation_id[len("team_"):]

        team = get_team_by_id_or_name(team_id)

        if not team:
            raise HTTPException(
                status_code=404,
                detail="Team not found.",
            )

        if not is_user_team_member(team, identifiers):
            raise HTTPException(
                status_code=403,
                detail="You are not a member of this team.",
            )

        return team

    if conversation_id.startswith("dm_"):

        canonical = resolve_canonical_user_id(
            identifiers.get("primary_id")
        )

        parts = conversation_id.split("_", 2)

        if len(parts) != 3:
            raise HTTPException(
                status_code=400,
                detail="Invalid direct conversation ID.",
            )

        if canonical not in {parts[1], parts[2]}:
            raise HTTPException(
                status_code=403,
                detail="You are not a participant in this conversation.",
            )

        return None

    raise HTTPException(
        status_code=400,
        detail="Invalid conversation ID.",
    )


# =============================================================================
# PYDANTIC SCHEMAS
# =============================================================================

class MessageCreate(BaseModel):
    chat_type: str
    target_id: str
    conversation_id: Optional[str] = None
    content: str
    attachments: Optional[List[Dict[str, Any]]] = []
    reply_to: Optional[Dict[str, Any]] = None


class ReactionToggle(BaseModel):
    emoji: str


class MarkReadRequest(BaseModel):
    chat_type: str
    target_id: str
    conversation_id: Optional[str] = None


class MessageUpdate(BaseModel):
    content: str


class GroupCreate(BaseModel):
    name: str
    description: Optional[str] = ""
    member_ids: Optional[List[str]] = []


class MemberAction(BaseModel):
    user_id: str


# =============================================================================
# CONTACTS
# =============================================================================

@router.get("/contacts", response_model=List[Dict[str, Any]])
def get_contacts(current_user=Depends(get_current_user)):

    my_ids = get_user_identifiers(current_user)
    my_keys = set(get_all_user_keys(my_ids))

    contacts = []
    seen_ids = set()

    admin_contact = {
        "id": "ADM-001",
        "employee_id": "ADM-001",
        "name": "Administrator",
        "email": "admin@arshith.com",
        "role": "admin",
        "department": "Administration",
        "designation": "Executive Administration",
        "avatar": "A",
        "profile_photo": None,
        "photo_url": None,
        "is_online": (
            manager.is_user_online("ADM-001")
            or manager.is_user_online("admin@arshith.com")
        ),
    }

    if (
        "adm-001" not in my_keys
        and "admin@arshith.com" not in my_keys
    ):

        contacts.append(admin_contact)
        seen_ids.add("adm-001")
        seen_ids.add("admin@arshith.com")

    employees = firestore_service.list_documents("employees")

    for employee in employees:

        employee_id = str(
            employee.get("employee_id")
            or employee.get("employeeId")
            or employee.get("id")
            or ""
        ).strip()

        email = str(
            employee.get("email") or ""
        ).strip().lower()

        name = (
            employee.get("full_name")
            or employee.get("name")
            or "Employee"
        )

        if not employee_id and not email:
            continue

        if (
            employee_id.lower() in my_keys
            or email in my_keys
        ):
            continue

        key_check = (employee_id or email).lower()

        if key_check in seen_ids:
            continue

        seen_ids.add(key_check)

        if email:
            seen_ids.add(email)

        photo = (
            employee.get("profile_photo")
            or employee.get("profilePhoto")
            or employee.get("photoUrl")
            or employee.get("photo_url")
            or employee.get("photo")
        )

        contacts.append({
            "id": employee_id or str(employee.get("id")),
            "employee_id": employee_id,
            "name": name,
            "email": email,
            "role": "employee",
            "department": employee.get(
                "department",
                "Operations",
            ),
            "designation": employee.get(
                "designation",
                "Staff Member",
            ),
            "avatar": name[0].upper() if name else "E",
            "profile_photo": photo,
            "photo_url": photo,
            "is_online": (
                manager.is_user_online(employee_id)
                or manager.is_user_online(email)
            ),
        })

    contacts.sort(
        key=lambda contact: (
            contact["role"] != "admin",
            contact["name"].lower(),
        )
    )

    return contacts


# =============================================================================
# TEAMS
# =============================================================================

@router.get("/teams", response_model=List[Dict[str, Any]])
def get_teams_for_chat(current_user=Depends(get_current_user)):

    my_ids = get_user_identifiers(current_user)

    all_teams = firestore_service.list_documents("teams")

    accessible_teams = []

    for team in all_teams:

        team_id = str(team.get("id") or "")

        team_name = (
            team.get("name")
            or team.get("teamName")
            or "Team"
        )

        members = get_team_member_keys(team)

        if not is_user_team_member(team, my_ids):
            continue

        accessible_teams.append({
            "id": team_id,
            "name": team_name,
            "department": team.get(
                "department",
                "Company",
            ),
            "description": team.get("description", ""),
            "leader": team.get(
                "leader",
                "Administrator",
            ),
            "members": list(members),
            "member_count": len(members),
            "is_admin": my_ids["role"] == "admin",
            "is_custom_group": bool(
                team.get("is_custom_group", False)
            ),
            "created_by": team.get("created_by", ""),
            "created_at": team.get("created_at", ""),
        })

    accessible_teams.sort(
        key=lambda team: (
            not ("general" in team["name"].lower()),
            team["name"].lower(),
        )
    )

    return accessible_teams


# =============================================================================
# CONVERSATIONS
# =============================================================================

@router.get("/conversations", response_model=List[Dict[str, Any]])
def get_conversations(current_user=Depends(get_current_user)):

    migrate_legacy_chat_messages()

    my_ids = get_user_identifiers(current_user)

    my_canonical = resolve_canonical_user_id(
        my_ids["primary_id"]
    )

    my_keys = set(get_all_user_keys(my_ids))

    messages = firestore_service.list_documents(
        "chat_messages"
    )

    messages.sort(
        key=lambda message: str(
            message.get("created_at", "")
        ),
        reverse=True,
    )

    accessible_teams = get_teams_for_chat(current_user)

    conversations_dict = {}
    team_key_to_cid = {}

    for team in accessible_teams:

        conversation_id = f"team_{team['id']}"

        conversations_dict[conversation_id] = {
            "conversation_id": conversation_id,
            "type": "team",
            "target_id": str(team["id"]),
            "title": team["name"],
            "subtitle": (
                f"{team.get('department', 'Company')} "
                f"• {team.get('member_count', 0)} members"
            ),
            "avatar": "#",
            "profile_photo": None,
            "photo_url": None,
            "leader": team.get("leader"),
            "last_message": "No messages yet",
            "last_message_sender": "",
            "last_message_at": team.get("created_at") or "",
            "unread_count": 0,
            "is_pinned": (
                "general" in team["name"].lower()
            ),
            "is_online": False,
        }

        team_key_to_cid[str(team["id"]).lower()] = conversation_id
        team_key_to_cid[str(team["name"]).lower()] = conversation_id

    direct_conversations = {}

    for message in messages:

        chat_type = message.get("chat_type", "direct")

        sender_id = str(
            message.get("sender_id", "")
        ).strip().lower()

        read_by = message.get("read_by", [])

        if not isinstance(read_by, list):
            read_by = []

        read_by_norm = {
            str(value).strip().lower()
            for value in read_by
        }

        is_unread = not bool(
            my_keys.intersection(read_by_norm)
        )

        if chat_type == "team":

            team_id = str(
                message.get("team_id")
                or message.get("target_id", "")
            ).strip().lower()

            team_name = str(
                message.get("team_name", "")
            ).strip().lower()

            matched_cid = (
                team_key_to_cid.get(team_id)
                or team_key_to_cid.get(team_name)
            )

            if matched_cid:

                conversation = conversations_dict[matched_cid]

                if conversation["last_message"] == "No messages yet":

                    conversation["last_message"] = (
                        message.get("content", "")
                        or (
                            "Shared an attachment"
                            if message.get("attachments")
                            else ""
                        )
                    )

                    conversation["last_message_sender"] = (
                        message.get("sender_name", "")
                    )

                    conversation["last_message_at"] = (
                        message.get("created_at", "")
                    )

                if is_unread and sender_id not in my_keys:
                    conversation["unread_count"] += 1

        elif chat_type == "direct":

            sender_canonical = resolve_canonical_user_id(
                message.get("sender_id", "")
            )

            receiver_canonical = resolve_canonical_user_id(
                message.get("receiver_id")
                or message.get("target_id", "")
            )

            participants = {
                sender_canonical,
                receiver_canonical,
            }

            user_in_chat = (
                my_canonical in participants
                or sender_canonical.lower() in my_keys
                or receiver_canonical.lower() in my_keys
            )

            if not user_in_chat:
                continue

            sender_is_me = (
                my_canonical == sender_canonical
                or sender_canonical.lower() in my_keys
            )

            other_canonical = (
                receiver_canonical
                if sender_is_me
                else sender_canonical
            )

            conversation_id = build_direct_conversation_id(
                my_canonical,
                other_canonical,
            )

            if conversation_id not in direct_conversations:

                profile = resolve_employee_profile(
                    other_canonical
                ) or {}

                display_title = (
                    profile.get("name")
                    or (
                        message.get("sender_name")
                        if not sender_is_me
                        else message.get("receiver_name")
                    )
                    or other_canonical
                )

                photo = (
                    profile.get("profile_photo")
                    or profile.get("photo_url")
                )

                direct_conversations[conversation_id] = {
                    "conversation_id": conversation_id,
                    "type": "direct",
                    "target_id": other_canonical,
                    "title": display_title,
                    "subtitle": (
                        f"{profile.get('designation', 'Staff')} "
                        f"• {profile.get('department', 'Company')}"
                    ),
                    "avatar": (
                        display_title[0].upper()
                        if display_title
                        else "U"
                    ),
                    "profile_photo": photo,
                    "photo_url": photo,
                    "last_message": (
                        message.get("content", "")
                        or (
                            "Shared an attachment"
                            if message.get("attachments")
                            else ""
                        )
                    ),
                    "last_message_sender": message.get(
                        "sender_name",
                        "",
                    ),
                    "last_message_at": message.get(
                        "created_at",
                        "",
                    ),
                    "unread_count": 0,
                    "is_pinned": False,
                    "is_online": manager.is_user_online(
                        other_canonical
                    ),
                }

            if is_unread and not sender_is_me:
                direct_conversations[
                    conversation_id
                ]["unread_count"] += 1

    contacts = get_contacts(current_user)

    contact_lookup = {}

    for contact in contacts:

        for key in [
            contact.get("id"),
            contact.get("employee_id"),
            contact.get("email"),
            contact.get("name"),
        ]:

            if key:
                contact_lookup[
                    str(key).strip().lower()
                ] = contact

    for conversation in direct_conversations.values():

        target_id = str(
            conversation["target_id"]
        ).strip().lower()

        contact = (
            contact_lookup.get(target_id)
            or resolve_employee_profile(
                conversation["target_id"]
            )
        )

        if contact:

            conversation["title"] = contact.get(
                "name",
                conversation["title"],
            )

            conversation["subtitle"] = (
                f"{contact.get('designation', 'Staff')} "
                f"• {contact.get('department', 'Company')}"
            )

            conversation["avatar"] = (
                contact["name"][0].upper()
                if contact.get("name")
                else "U"
            )

            photo = (
                contact.get("profile_photo")
                or contact.get("photo_url")
            )

            conversation["profile_photo"] = photo
            conversation["photo_url"] = photo

            if "is_online" in contact:
                conversation["is_online"] = contact["is_online"]

    results = (
        list(conversations_dict.values())
        + list(direct_conversations.values())
    )

    results.sort(
        key=lambda conversation: (
            conversation.get("is_pinned", False),
            str(conversation.get("last_message_at", "")),
        ),
        reverse=True,
    )

    return results


# =============================================================================
# GET MESSAGES
# =============================================================================

@router.get("/messages", response_model=List[Dict[str, Any]])
def get_messages(
    chat_type: Optional[str] = Query(None),
    target_id: Optional[str] = Query(None),
    conversation_id: Optional[str] = Query(None),
    limit: int = Query(60, ge=1, le=200),
    current_user=Depends(get_current_user),
):

    migrate_legacy_chat_messages()

    my_ids = get_user_identifiers(current_user)

    my_canonical = resolve_canonical_user_id(
        my_ids["primary_id"]
    )

    my_keys = set(get_all_user_keys(my_ids))

    if conversation_id and conversation_id.strip():

        expected_cid = conversation_id.strip()

    elif chat_type == "team" and target_id:

        team = get_team_by_id_or_name(target_id)

        if not team:
            raise HTTPException(
                status_code=404,
                detail="Team not found.",
            )

        expected_cid = f"team_{team.get('id')}"

    elif chat_type == "direct" and target_id:

        target_canonical = resolve_canonical_user_id(
            target_id
        )

        expected_cid = build_direct_conversation_id(
            my_canonical,
            target_canonical,
        )

    elif target_id:

        team = get_team_by_id_or_name(target_id)

        if team:
            expected_cid = f"team_{team.get('id')}"
        else:
            target_canonical = resolve_canonical_user_id(
                target_id
            )

            expected_cid = build_direct_conversation_id(
                my_canonical,
                target_canonical,
            )

    else:

        raise HTTPException(
            status_code=400,
            detail="Either conversation_id or target_id must be provided.",
        )

    validate_conversation_access(
        expected_cid,
        my_ids,
    )

    all_messages = firestore_service.list_documents(
        "chat_messages"
    )

    filtered = []
    messages_to_mark_read = []

    for message in all_messages:

        message_cid = get_message_conversation_id(message)

        if message_cid != expected_cid:
            continue

        filtered.append(message)

        read_by = message.get("read_by", [])

        if not isinstance(read_by, list):
            read_by = []

        read_by_norm = {
            str(value).strip().lower()
            for value in read_by
        }

        if not my_keys.intersection(read_by_norm):

            message_id = str(message.get("id") or "")

            if message_id:
                messages_to_mark_read.append(message_id)

    for message_id in messages_to_mark_read:

        document = firestore_service.get_document(
            "chat_messages",
            message_id,
        )

        if not document:
            continue

        read_by = document.get("read_by", [])

        if not isinstance(read_by, list):
            read_by = []

        if my_canonical not in read_by:

            read_by.append(my_canonical)

            firestore_service.update_document(
                "chat_messages",
                message_id,
                {"read_by": read_by},
            )

    filtered.sort(
        key=lambda message: str(
            message.get("created_at", "")
        )
    )

    return filtered[-limit:]


# =============================================================================
# SEND MESSAGE
# =============================================================================

@router.post(
    "/messages",
    status_code=status.HTTP_201_CREATED,
    response_model=Dict[str, Any],
)
async def send_message(
    payload: MessageCreate,
    current_user=Depends(get_current_user),
):

    content = payload.content.strip()

    if not content and not payload.attachments:
        raise HTTPException(
            status_code=400,
            detail="Message content or attachment is required.",
        )

    if payload.chat_type not in {"direct", "team"}:
        raise HTTPException(
            status_code=400,
            detail="chat_type must be direct or team.",
        )

    my_ids = get_user_identifiers(current_user)

    my_canonical = resolve_canonical_user_id(
        my_ids["primary_id"]
    )

    now_iso = datetime.now(timezone.utc).isoformat()

    clean_target = payload.target_id.strip()

    team_name = ""
    canonical_target_id = clean_target
    recipient_name = ""
    recipient_keys = []
    participant_ids = []
    team = None

    if payload.chat_type == "team":

        team = get_team_by_id_or_name(clean_target)

        if not team:
            raise HTTPException(
                status_code=404,
                detail="Team not found.",
            )

        if not is_user_team_member(team, my_ids):
            raise HTTPException(
                status_code=403,
                detail="You are not authorized to send messages to this team.",
            )

        canonical_target_id = str(team.get("id"))

        team_name = (
            team.get("name")
            or team.get("teamName")
            or "Team Channel"
        )

        participant_ids = [canonical_target_id]

        recipient_keys = get_team_websocket_keys(team)

        cid = f"team_{canonical_target_id}"

        receiver_id = canonical_target_id
        recipient_name = team_name

    else:

        target_canonical = resolve_canonical_user_id(
            clean_target
        )

        if not target_canonical:
            raise HTTPException(
                status_code=400,
                detail="Invalid recipient.",
            )

        if target_canonical == my_canonical:
            raise HTTPException(
                status_code=400,
                detail="You cannot send a direct message to yourself.",
            )

        canonical_target_id = target_canonical
        receiver_id = target_canonical

        cid = build_direct_conversation_id(
            my_canonical,
            target_canonical,
        )

        participant_ids = sorted([
            my_canonical,
            target_canonical,
        ])

        if target_canonical == "ADM-001":

            recipient_name = "Administrator"

            recipient_keys = [
                "adm-001",
                "admin",
                "administrator",
                "admin@arshith.com",
            ]

        else:

            recipient_keys = [target_canonical.lower()]

            employee = firestore_service.get_document(
                "employees",
                target_canonical,
            )

            if not employee:

                employees = firestore_service.list_documents(
                    "employees",
                    [("employee_id", "==", target_canonical)],
                )

                if employees:
                    employee = employees[0]

            if employee:

                recipient_name = (
                    employee.get("full_name")
                    or employee.get("name")
                    or target_canonical
                )

                if employee.get("employee_id"):
                    recipient_keys.append(
                        str(employee["employee_id"]).lower()
                    )

                if employee.get("email"):
                    recipient_keys.append(
                        str(employee["email"]).lower()
                    )

                if employee.get("uid"):
                    recipient_keys.append(
                        str(employee["uid"]).lower()
                    )

                if employee.get("user_id"):
                    recipient_keys.append(
                        str(employee["user_id"]).lower()
                    )

    my_profile = (
        resolve_employee_profile(my_canonical) or {}
    )

    sender_photo = (
        my_profile.get("profile_photo")
        or my_profile.get("photo_url")
        or my_profile.get("photo")
    )

    sender_display_name = (
        my_profile.get("name")
        or my_ids["name"]
    )

    message_data = {
        "conversation_id": cid,
        "participant_ids": participant_ids,
        "chat_type": payload.chat_type,
        "target_id": canonical_target_id,
        "team_id": (
            canonical_target_id
            if payload.chat_type == "team"
            else None
        ),
        "team_name": team_name,
        "sender_id": my_canonical,
        "sender_name": sender_display_name,
        "sender_role": my_ids["role"],
        "sender_avatar": (
            sender_display_name[0].upper()
            if sender_display_name
            else "U"
        ),
        "sender_photo": sender_photo,
        "receiver_id": receiver_id,
        "receiver_name": recipient_name,
        "content": content,
        "attachments": payload.attachments or [],
        "reply_to": payload.reply_to,
        "reactions": {},
        "is_pinned": False,
        "read_by": [my_canonical],
        "created_at": now_iso,
        "updated_at": now_iso,
    }

    document_id = firestore_service.create_document(
        "chat_messages",
        message_data,
    )

    message_data["id"] = document_id

    event_payload = {
        "type": "new_message",
        "conversation_id": cid,
        "message": message_data,
    }

    if payload.chat_type == "team":

        # Team message goes only to team members.
        all_targets = list(
            set(
                recipient_keys
                + get_all_user_keys(my_ids)
            )
        )

        await manager.broadcast_to_keys(
            all_targets,
            event_payload,
        )

    else:

        # Direct message goes only to sender and recipient.
        all_targets = list(
            set(
                recipient_keys
                + get_all_user_keys(my_ids)
            )
        )

        await manager.broadcast_to_keys(
            all_targets,
            event_payload,
        )

    return message_data


# =============================================================================
# REACTIONS
# =============================================================================

@router.post(
    "/messages/{message_id}/reactions",
    response_model=Dict[str, Any],
)
async def toggle_reaction(
    message_id: str,
    payload: ReactionToggle,
    current_user=Depends(get_current_user),
):

    document = firestore_service.get_document(
        "chat_messages",
        message_id,
    )

    if not document:
        raise HTTPException(
            status_code=404,
            detail="Message not found.",
        )

    my_ids = get_user_identifiers(current_user)

    conversation_id = get_message_conversation_id(document)

    team = validate_conversation_access(
        conversation_id,
        my_ids,
    )

    reactions = document.get("reactions", {})

    if not isinstance(reactions, dict):
        reactions = {}

    emoji = payload.emoji.strip()

    if not emoji:
        raise HTTPException(
            status_code=400,
            detail="Emoji character is required.",
        )

    user_key = my_ids["primary_id"]

    user_list = reactions.get(emoji, [])

    if not isinstance(user_list, list):
        user_list = []

    if user_key in user_list:

        user_list.remove(user_key)

        if user_list:
            reactions[emoji] = user_list
        else:
            reactions.pop(emoji, None)

    else:

        user_list.append(user_key)
        reactions[emoji] = user_list

    firestore_service.update_document(
        "chat_messages",
        message_id,
        {"reactions": reactions},
    )

    event = {
        "type": "reaction_updated",
        "message_id": message_id,
        "conversation_id": conversation_id,
        "reactions": reactions,
    }

    if team:
        await manager.broadcast_to_keys(
            get_team_websocket_keys(team),
            event,
        )
    else:
        await manager.broadcast_all(event)

    return {
        "success": True,
        "reactions": reactions,
    }


# =============================================================================
# PIN MESSAGE
# =============================================================================

@router.post(
    "/messages/{message_id}/pin",
    response_model=Dict[str, Any],
)
async def toggle_pin(
    message_id: str,
    current_user=Depends(get_current_user),
):

    document = firestore_service.get_document(
        "chat_messages",
        message_id,
    )

    if not document:
        raise HTTPException(
            status_code=404,
            detail="Message not found.",
        )

    my_ids = get_user_identifiers(current_user)

    conversation_id = get_message_conversation_id(document)

    team = validate_conversation_access(
        conversation_id,
        my_ids,
    )

    new_pinned = not bool(
        document.get("is_pinned", False)
    )

    firestore_service.update_document(
        "chat_messages",
        message_id,
        {"is_pinned": new_pinned},
    )

    event = {
        "type": "pin_updated",
        "message_id": message_id,
        "conversation_id": conversation_id,
        "is_pinned": new_pinned,
    }

    if team:
        await manager.broadcast_to_keys(
            get_team_websocket_keys(team),
            event,
        )
    else:
        await manager.broadcast_all(event)

    return {
        "success": True,
        "is_pinned": new_pinned,
    }


# =============================================================================
# DELETE MESSAGE
# =============================================================================

@router.delete(
    "/messages/{message_id}",
    response_model=Dict[str, Any],
)
async def delete_message(
    message_id: str,
    current_user=Depends(get_current_user),
):

    my_ids = get_user_identifiers(current_user)

    my_keys = set(get_all_user_keys(my_ids))

    document = firestore_service.get_document(
        "chat_messages",
        message_id,
    )

    if not document:
        raise HTTPException(
            status_code=404,
            detail="Message not found.",
        )

    conversation_id = get_message_conversation_id(document)

    team = validate_conversation_access(
        conversation_id,
        my_ids,
    )

    sender_id = str(
        document.get("sender_id", "")
    ).strip().lower()

    is_admin = my_ids["role"] == "admin"

    if sender_id not in my_keys and not is_admin:
        raise HTTPException(
            status_code=403,
            detail="You can only delete your own messages.",
        )

    firestore_service.delete_document(
        "chat_messages",
        message_id,
    )

    event = {
        "type": "message_deleted",
        "message_id": message_id,
        "conversation_id": conversation_id,
    }

    if team:
        await manager.broadcast_to_keys(
            get_team_websocket_keys(team),
            event,
        )
    else:
        await manager.broadcast_all(event)

    return {
        "success": True,
        "message": "Message deleted successfully.",
    }


# =============================================================================
# MARK CONVERSATION READ
# =============================================================================

@router.post(
    "/read",
    response_model=Dict[str, Any],
)
def mark_conversation_read(
    payload: MarkReadRequest,
    current_user=Depends(get_current_user),
):

    my_ids = get_user_identifiers(current_user)

    my_canonical = resolve_canonical_user_id(
        my_ids["primary_id"]
    )

    my_keys = set(get_all_user_keys(my_ids))

    if payload.conversation_id:

        target_cid = payload.conversation_id.strip()

    elif payload.chat_type == "team":

        team = get_team_by_id_or_name(
            payload.target_id.strip()
        )

        if not team:
            raise HTTPException(
                status_code=404,
                detail="Team not found.",
            )

        target_cid = f"team_{team.get('id')}"

    elif payload.chat_type == "direct":

        target_canonical = resolve_canonical_user_id(
            payload.target_id
        )

        target_cid = build_direct_conversation_id(
            my_canonical,
            target_canonical,
        )

    else:

        raise HTTPException(
            status_code=400,
            detail="Invalid chat type.",
        )

    validate_conversation_access(
        target_cid,
        my_ids,
    )

    messages = firestore_service.list_documents(
        "chat_messages"
    )

    updated_count = 0

    for message in messages:

        if get_message_conversation_id(message) != target_cid:
            continue

        read_by = message.get("read_by", [])

        if not isinstance(read_by, list):
            read_by = []

        read_by_norm = {
            str(value).strip().lower()
            for value in read_by
        }

        if my_keys.intersection(read_by_norm):
            continue

        read_by.append(my_canonical)

        firestore_service.update_document(
            "chat_messages",
            str(message.get("id")),
            {"read_by": read_by},
        )

        updated_count += 1

    return {
        "success": True,
        "marked_count": updated_count,
    }


# =============================================================================
# UNREAD COUNT
# =============================================================================

@router.get(
    "/unread-count",
    response_model=Dict[str, int],
)
def get_unread_count(current_user=Depends(get_current_user)):

    my_ids = get_user_identifiers(current_user)

    my_canonical = resolve_canonical_user_id(
        my_ids["primary_id"]
    )

    my_keys = set(get_all_user_keys(my_ids))

    accessible_teams = get_teams_for_chat(current_user)

    accessible_team_cids = {
        f"team_{team['id']}"
        for team in accessible_teams
    }

    messages = firestore_service.list_documents(
        "chat_messages"
    )

    unread = 0

    for message in messages:

        sender_id = str(
            message.get("sender_id", "")
        ).strip().lower()

        if (
            sender_id in my_keys
            or sender_id == my_canonical.lower()
        ):
            continue

        read_by = message.get("read_by", [])

        if not isinstance(read_by, list):
            read_by = []

        read_by_norm = {
            str(value).strip().lower()
            for value in read_by
        }

        if my_keys.intersection(read_by_norm):
            continue

        chat_type = message.get("chat_type", "direct")

        if chat_type == "direct":

            receiver_canonical = resolve_canonical_user_id(
                message.get("receiver_id")
                or message.get("target_id", "")
            )

            if (
                receiver_canonical == my_canonical
                or receiver_canonical.lower() in my_keys
            ):
                unread += 1

        elif chat_type == "team":

            message_cid = get_message_conversation_id(
                message
            )

            if message_cid in accessible_team_cids:
                unread += 1

    return {
        "total_unread": unread,
    }


# =============================================================================
# EDIT MESSAGE
# =============================================================================

@router.patch(
    "/messages/{message_id}",
    response_model=Dict[str, Any],
)
async def update_message(
    message_id: str,
    payload: MessageUpdate,
    current_user=Depends(get_current_user),
):

    my_ids = get_user_identifiers(current_user)

    my_keys = set(get_all_user_keys(my_ids))

    document = firestore_service.get_document(
        "chat_messages",
        message_id,
    )

    if not document:
        raise HTTPException(
            status_code=404,
            detail="Message not found.",
        )

    conversation_id = get_message_conversation_id(document)

    team = validate_conversation_access(
        conversation_id,
        my_ids,
    )

    sender_id = str(
        document.get("sender_id", "")
    ).strip().lower()

    is_admin = my_ids["role"] == "admin"

    if sender_id not in my_keys and not is_admin:
        raise HTTPException(
            status_code=403,
            detail="You can only edit your own messages.",
        )

    new_content = payload.content.strip()

    if not new_content:
        raise HTTPException(
            status_code=400,
            detail="Message content cannot be empty.",
        )

    now_iso = datetime.now(timezone.utc).isoformat()

    updates = {
        "content": new_content,
        "is_edited": True,
        "updated_at": now_iso,
    }

    firestore_service.update_document(
        "chat_messages",
        message_id,
        updates,
    )

    document.update(updates)

    event = {
        "type": "message_edited",
        "message_id": message_id,
        "conversation_id": conversation_id,
        "content": new_content,
        "is_edited": True,
        "updated_at": now_iso,
    }

    if team:
        await manager.broadcast_to_keys(
            get_team_websocket_keys(team),
            event,
        )
    else:
        await manager.broadcast_all(event)

    return {
        "success": True,
        "message": document,
    }


# =============================================================================
# ATTACHMENT UPLOAD
# =============================================================================

@router.post(
    "/attachments/upload",
    response_model=Dict[str, Any],
)
async def upload_chat_attachment(
    file: UploadFile = File(...),
    current_user=Depends(get_current_user),
):

    filename = file.filename or "attachment"

    ext = os.path.splitext(filename)[1].lower()

    if ext not in ALLOWED_EXTENSIONS:

        raise HTTPException(
            status_code=400,
            detail=(
                f"File extension '{ext}' is not permitted. "
                f"Allowed: {', '.join(sorted(ALLOWED_EXTENSIONS))}"
            ),
        )

    contents = await file.read()

    if len(contents) > MAX_FILE_SIZE:

        raise HTTPException(
            status_code=400,
            detail="File size exceeds maximum allowed limit of 10MB.",
        )

    safe_filename = (
        f"{uuid.uuid4().hex[:12]}_"
        f"{os.path.basename(filename).replace(' ', '_')}"
    )

    target_path = os.path.join(
        UPLOAD_DIR,
        safe_filename,
    )

    with open(target_path, "wb") as output_file:
        output_file.write(contents)

    is_image = ext in {
        ".png",
        ".jpg",
        ".jpeg",
        ".gif",
        ".webp",
    }

    is_audio = ext in {
        ".webm",
        ".mp3",
        ".wav",
        ".ogg",
        ".m4a",
        ".aac",
    }

    file_info = {
        "url": f"/static/chat_uploads/{safe_filename}",
        "filename": filename,
        "file_size": len(contents),
        "content_type": (
            file.content_type
            or (
                "image/" + ext[1:]
                if is_image
                else (
                    "audio/" + ext[1:]
                    if is_audio
                    else "application/octet-stream"
                )
            )
        ),
        "is_image": is_image,
        "is_audio": is_audio,
    }

    return file_info


# =============================================================================
# CREATE GROUP
# =============================================================================

@router.post(
    "/conversations/group",
    response_model=Dict[str, Any],
)
async def create_group_channel(
    payload: GroupCreate,
    current_user=Depends(get_current_user),
):

    my_ids = get_user_identifiers(current_user)

    name = payload.name.strip()

    if not name:
        raise HTTPException(
            status_code=400,
            detail="Group name is required.",
        )

    members = list(set(
        [my_ids["primary_id"]]
        + [
            str(member).strip()
            for member in (payload.member_ids or [])
            if str(member).strip()
        ]
    ))

    now_iso = datetime.now(timezone.utc).isoformat()

    group_data = {
        "name": name,
        "teamName": name,
        "description": (
            payload.description.strip()
            if payload.description
            else ""
        ),
        "department": "Custom Group",
        "leader": my_ids["name"],
        "created_by": my_ids["primary_id"],
        "created_at": now_iso,
        "members": members,
        "memberIds": members,
        "is_custom_group": True,
    }

    team_id = firestore_service.create_document(
        "teams",
        group_data,
    )

    group_data["id"] = team_id

    conversation_id = f"team_{team_id}"

    system_message = {
        "conversation_id": conversation_id,
        "participant_ids": [str(team_id)],
        "chat_type": "team",
        "target_id": str(team_id),
        "team_id": str(team_id),
        "team_name": name,
        "sender_id": "SYSTEM",
        "sender_name": "System",
        "sender_role": "system",
        "sender_avatar": "⚙️",
        "receiver_id": str(team_id),
        "receiver_name": name,
        "content": (
            f"Group '{name}' was created by {my_ids['name']}."
        ),
        "attachments": [],
        "reply_to": None,
        "reactions": {},
        "is_pinned": False,
        "read_by": [my_ids["primary_id"]],
        "created_at": now_iso,
        "updated_at": now_iso,
    }

    message_id = firestore_service.create_document(
        "chat_messages",
        system_message,
    )

    system_message["id"] = message_id

    await manager.broadcast_to_keys(
        get_team_websocket_keys(group_data),
        {
            "type": "new_team",
            "team": group_data,
        },
    )

    return group_data


# =============================================================================
# ADD GROUP MEMBER
# =============================================================================

@router.post(
    "/conversations/{group_id}/members",
    response_model=Dict[str, Any],
)
async def add_group_member(
    group_id: str,
    payload: MemberAction,
    current_user=Depends(get_current_user),
):

    my_ids = get_user_identifiers(current_user)

    team = firestore_service.get_document(
        "teams",
        group_id,
    )

    if not team:
        raise HTTPException(
            status_code=404,
            detail="Group not found.",
        )

    if my_ids["role"] != "admin":

        created_by = str(
            team.get("created_by", "")
        ).strip().lower()

        if created_by not in set(get_all_user_keys(my_ids)):
            raise HTTPException(
                status_code=403,
                detail="Only the group creator or administrator can add members.",
            )

    target_user = payload.user_id.strip()

    if not target_user:
        raise HTTPException(
            status_code=400,
            detail="User ID is required.",
        )

    members = team.get("members", [])

    if not isinstance(members, list):
        members = []

    existing_keys = get_team_member_keys(team)

    target_keys = _normalize_member_keys(target_user)

    if not existing_keys.intersection(target_keys):

        members.append(target_user)

        firestore_service.update_document(
            "teams",
            group_id,
            {
                "members": members,
                "memberIds": members,
            },
        )

        now_iso = datetime.now(timezone.utc).isoformat()

        conversation_id = f"team_{group_id}"

        system_message = {
            "conversation_id": conversation_id,
            "participant_ids": [str(group_id)],
            "chat_type": "team",
            "target_id": group_id,
            "team_id": group_id,
            "team_name": (
                team.get("name")
                or team.get("teamName")
                or "Group"
            ),
            "sender_id": "SYSTEM",
            "sender_name": "System",
            "sender_role": "system",
            "sender_avatar": "⚙️",
            "receiver_id": group_id,
            "receiver_name": team.get("name", "Group"),
            "content": (
                f"{target_user} was added to the group "
                f"by {my_ids['name']}."
            ),
            "attachments": [],
            "reply_to": None,
            "reactions": {},
            "is_pinned": False,
            "read_by": [my_ids["primary_id"]],
            "created_at": now_iso,
            "updated_at": now_iso,
        }

        message_id = firestore_service.create_document(
            "chat_messages",
            system_message,
        )

        system_message["id"] = message_id

        updated_team = dict(team)
        updated_team["members"] = members
        updated_team["memberIds"] = members

        await manager.broadcast_to_keys(
            get_team_websocket_keys(updated_team),
            {
                "type": "new_message",
                "conversation_id": conversation_id,
                "message": system_message,
            },
        )

    return {
        "success": True,
        "members": members,
    }


# =============================================================================
# REMOVE GROUP MEMBER
# =============================================================================

@router.delete(
    "/conversations/{group_id}/members/{user_id}",
    response_model=Dict[str, Any],
)
async def remove_group_member(
    group_id: str,
    user_id: str,
    current_user=Depends(get_current_user),
):

    my_ids = get_user_identifiers(current_user)

    team = firestore_service.get_document(
        "teams",
        group_id,
    )

    if not team:
        raise HTTPException(
            status_code=404,
            detail="Group not found.",
        )

    target_user = user_id.strip()

    is_self = user_matches_target(
        my_ids,
        target_user,
    )

    is_admin = my_ids["role"] == "admin"

    created_by = str(
        team.get("created_by", "")
    ).strip().lower()

    can_manage = (
        is_admin
        or created_by in set(get_all_user_keys(my_ids))
    )

    if not is_self and not can_manage:

        raise HTTPException(
            status_code=403,
            detail="Permission denied to remove member.",
        )

    members = team.get("members", [])

    if not isinstance(members, list):
        members = []

    target_keys = _normalize_member_keys(target_user)

    new_members = []

    for member in members:

        member_keys = _normalize_member_keys(member)

        if member_keys.intersection(target_keys):
            continue

        new_members.append(member)

    firestore_service.update_document(
        "teams",
        group_id,
        {
            "members": new_members,
            "memberIds": new_members,
        },
    )

    now_iso = datetime.now(timezone.utc).isoformat()

    team_name = (
        team.get("name")
        or team.get("teamName")
        or "Group"
    )

    action_text = (
        f"{target_user} left the group."
        if is_self
        else (
            f"{target_user} was removed by "
            f"{my_ids['name']}."
        )
    )

    conversation_id = f"team_{group_id}"

    system_message = {
        "conversation_id": conversation_id,
        "participant_ids": [str(group_id)],
        "chat_type": "team",
        "target_id": group_id,
        "team_id": group_id,
        "team_name": team_name,
        "sender_id": "SYSTEM",
        "sender_name": "System",
        "sender_role": "system",
        "sender_avatar": "⚙️",
        "receiver_id": group_id,
        "receiver_name": team_name,
        "content": action_text,
        "attachments": [],
        "reply_to": None,
        "reactions": {},
        "is_pinned": False,
        "read_by": [my_ids["primary_id"]],
        "created_at": now_iso,
        "updated_at": now_iso,
    }

    message_id = firestore_service.create_document(
        "chat_messages",
        system_message,
    )

    system_message["id"] = message_id

    updated_team = dict(team)
    updated_team["members"] = new_members
    updated_team["memberIds"] = new_members

    await manager.broadcast_to_keys(
        get_team_websocket_keys(updated_team),
        {
            "type": "new_message",
            "conversation_id": conversation_id,
            "message": system_message,
        },
    )

    return {
        "success": True,
        "members": new_members,
    }


# =============================================================================
# WEBSOCKET AUTHENTICATION
# =============================================================================

@router.websocket("/ws")
async def chat_websocket_endpoint(
    websocket: WebSocket,
    token: Optional[str] = Query(None),
):

    user_data = None

    if token:

        try:

            token_payload = decode_access_token(token)

            if token_payload:

                user_data = {
                    "id": token_payload.get("sub"),
                    "role": token_payload.get(
                        "role",
                        "employee",
                    ),
                    "email": token_payload.get("email"),
                    "employee_id": (
                        token_payload.get("employeeId")
                        or token_payload.get("employee_id")
                    ),
                }

        except Exception as exc:

            logger.debug(
                "Access token validation failed: %s",
                exc,
            )

        if not user_data:

            try:

                firebase_user = verify_firebase_token(token)

                if firebase_user:

                    user_data = {
                        "id": firebase_user.get("uid"),
                        "role": (
                            firebase_user.get("role")
                            or (
                                "admin"
                                if firebase_user.get("admin")
                                else "employee"
                            )
                        ),
                        "email": firebase_user.get("email"),
                        "employee_id": (
                            firebase_user.get("employee_id")
                            or firebase_user.get("employeeId")
                        ),
                    }

            except Exception as exc:

                logger.debug(
                    "Firebase token validation failed: %s",
                    exc,
                )

    if (
        not user_data
        or not (
            user_data.get("id")
            or user_data.get("employee_id")
        )
    ):

        await websocket.close(
            code=4401,
            reason="Unauthorized",
        )

        return

    class TempUser:
        pass

    temporary_user = TempUser()

    for key, value in user_data.items():
        setattr(temporary_user, key, value)

    user_ids = get_user_identifiers(temporary_user)

    user_keys = get_all_user_keys(user_ids)

    await manager.connect(
        websocket,
        user_keys,
    )

    await manager.broadcast_all({
        "type": "presence",
        "user_id": user_ids["primary_id"],
        "status": "online",
    })

    try:

        while True:

            data_text = await websocket.receive_text()

            try:

                data = json.loads(data_text)

                action = data.get("action")

                if action == "typing":

                    target_id = str(
                        data.get("target_id", "")
                    ).strip()

                    chat_type = data.get(
                        "chat_type",
                        "direct",
                    )

                    event = {
                        "type": "typing",
                        "sender_id": user_ids["primary_id"],
                        "sender_name": user_ids["name"],
                        "chat_type": chat_type,
                        "target_id": target_id,
                        "is_typing": bool(
                            data.get("is_typing", True)
                        ),
                    }

                    if chat_type == "direct":

                        target_keys = [
                            target_id,
                            resolve_canonical_user_id(target_id),
                        ]

                        await manager.broadcast_to_keys(
                            target_keys,
                            event,
                        )

                    elif chat_type == "team":

                        team = get_team_by_id_or_name(
                            target_id
                        )

                        if team and is_user_team_member(
                            team,
                            user_ids,
                        ):

                            await manager.broadcast_to_keys(
                                get_team_websocket_keys(team),
                                event,
                            )

                elif action == "ping":

                    await websocket.send_text(
                        json.dumps({
                            "type": "pong",
                        })
                    )

            except json.JSONDecodeError:

                logger.debug(
                    "Invalid WebSocket JSON received."
                )

    except WebSocketDisconnect:

        manager.disconnect(
            websocket,
            user_keys,
        )

        await manager.broadcast_all({
            "type": "presence",
            "user_id": user_ids["primary_id"],
            "status": "offline",
        })

    except Exception as exc:

        logger.debug(
            "Chat WebSocket closed with error: %s",
            exc,
        )

        manager.disconnect(
            websocket,
            user_keys,
        )

        await manager.broadcast_all({
            "type": "presence",
            "user_id": user_ids["primary_id"],
            "status": "offline",
        })