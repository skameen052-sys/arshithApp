package com.example.arshithconnectattendance;

public class Contact {
    // uid is the business ID used in chat_messages (AFI-... / ADM-...).
    private String uid;
    // authUid is the Firebase Authentication UID used by teams.memberIds.
    private String authUid;
    private String employee_id;
    private String name;
    private String email;
    private String designation;
    private String department;
    private String role;
    private String profile_photo;

    public Contact() { }

    public String getUid() { return uid == null ? "" : uid; }
    public void setUid(String value) { uid = value; }

    public String getAuthUid() { return authUid == null ? "" : authUid; }
    public void setAuthUid(String value) { authUid = value; }

    public String getEmployee_id() { return employee_id == null ? "" : employee_id; }
    public void setEmployee_id(String value) { employee_id = value; }

    public String getName() { return name == null ? "Employee" : name; }
    public void setName(String value) { name = value; }

    public String getEmail() { return email == null ? "" : email; }
    public void setEmail(String value) { email = value; }

    public String getDesignation() { return designation == null ? "" : designation; }
    public void setDesignation(String value) { designation = value; }

    public String getDepartment() { return department == null ? "" : department; }
    public void setDepartment(String value) { department = value; }

    public String getRole() { return role == null ? "Employee" : role; }
    public void setRole(String value) { role = value; }

    public String getProfile_photo() { return profile_photo == null ? "" : profile_photo; }
    public void setProfile_photo(String value) { profile_photo = value; }
}
