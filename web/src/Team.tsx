import { useState } from "react";
import { Copy, Pencil, UserRoundMinus } from "lucide-react";
import { api } from "./api";
import { type Org, type Project, type Role } from "./domain";
import { Add, Busy, Confirm, Dialog, Field, FormDialog, Heading, Problem, Select, Tag, field, useResource, useToast } from "./ui";

type Member = { userId: string; fullName: string; email: string; role: Role; projectId: string | null };
export function Team({ org, projects, userId, changed }: { org: Org; projects: Project[]; userId: string; changed: () => void }) {
  const path = "/orgs/" + org.id + "/members";
  const members = useResource(org.id, () => api<Member[]>(path));
  const pending = useResource(org.id, () => api<{email:string; role:Role; projectId:string|null; expiresAt:string}[]>(path+"/invitations"));
  const [cancelInvite, setCancelInvite] = useState("");
  const [editing, setEditing] = useState<Member | "invite" | null>(null);
  const [removing, setRemoving] = useState<Member | null>(null);
  const [link, setLink] = useState("");
  const [role, setRole] = useState<Role>("EMPLOYEE");
  const toast = useToast();
  const signIn = location.origin + location.pathname + "#login/" + org.id;
  function edit(member: Member | "invite") {
    setRole(member === "invite" ? (projects.length ? "EMPLOYEE" : "ADMIN") : member.role);
    setEditing(member);
  }
  async function copy(value: string) {
    try { await navigator.clipboard.writeText(value); toast("Link copied"); }
    catch { toast("Select the link and copy it manually."); }
  }
  const selected = editing && editing !== "invite" ? editing : null;
  const refresh = () => { members.reload(); changed(); };
  return <>
    <Heading title="Team access" sub={"People and project assignments in " + org.name + "."}
      action={<Add onClick={() => edit("invite")}>Invite member</Add>} />
    <div className="panel pad sign-in-link"><Field label="Organization sign-in link">
      <input readOnly value={signIn} onFocus={e => e.target.select()} /></Field>
      <button onClick={() => copy(signIn)}><Copy size={16}/>Copy link</button></div>
    <div className="section-heading"><h2>Members</h2></div>
    <div className="panel">{members.error ? <Problem error={members.error} retry={members.reload}/> : !members.data ? <Busy/> : members.data.map(m =>
      <div key={m.userId} className="member-row">
        <div className="grow"><strong>{m.fullName}{m.userId === userId ? " (you)" : ""}</strong><small>{m.email}</small></div>
        <Tag>{m.role === "ADMIN" ? "Admin" : "Employee"}</Tag>
        <span>{m.role === "ADMIN" ? "All projects" : projects.find(p => p.id === m.projectId)?.name || "Unassigned"}</span>
        <button className="icon" aria-label={"Edit access for " + m.fullName} onClick={() => edit(m)}><Pencil size={16}/></button>
        <button className="icon" aria-label={"Remove " + m.fullName} onClick={() => setRemoving(m)}><UserRoundMinus size={17}/></button>
      </div>)}</div>
    {pending.error && <Problem error={pending.error} retry={pending.reload}/>}
    {!!pending.data?.length && <><div className="section-heading"><h2>Pending invitations</h2></div><div className="panel">
      {pending.data.map(invite => <div className="member-row" key={invite.email}><div className="grow">
        <strong>{invite.email}</strong><small>Expires {new Date(invite.expiresAt).toLocaleDateString()}</small></div>
        <span>{invite.role === "ADMIN" ? "All projects" : projects.find(p => p.id === invite.projectId)?.name}</span>
        <button onClick={() => setCancelInvite(invite.email)}>Cancel invitation</button></div>)}</div></>}
    {cancelInvite && <Confirm title="Cancel invitation?" detail={"The link for " + cancelInvite + " will stop working."}
      submit="Cancel invitation" close={() => setCancelInvite("")} onSave={async () => {
        await api(path+"/invitations/cancel","POST",{email:cancelInvite}); pending.reload(); toast("Invitation cancelled");
      }}/>}
    {editing && <FormDialog title={selected ? "Edit access" : "Invite member"} submit={selected ? "Save access" : "Create invitation"}
      close={() => setEditing(null)} onSave={async d => {
        const assignment = { role, projectId: role === "EMPLOYEE" ? field(d, "project") : null };
        if (selected) {
          await api(path + "/" + selected.userId + "/role", "PUT", assignment);
          refresh(); toast("Access updated");
        } else {
          const result = await api<{token: string}>(path + "/invitations", "POST", { email: field(d, "email"), ...assignment });
          pending.reload();
          setLink(location.origin + location.pathname + "#join/" + result.token);
        }
      }}>
      {selected ? <p>{selected.email}</p> : <Field label="Email address"><input required type="email" name="email" maxLength={255}/></Field>}
      <Field label="Role"><Select value={role} onChange={e => setRole(e.target.value as Role)}>
        <option value="EMPLOYEE" disabled={!projects.length}>Employee</option><option value="ADMIN">Admin</option></Select></Field>
      {role === "EMPLOYEE" ? <Field label="Assigned project"><Select required name="project" defaultValue={selected?.projectId || projects[0]?.id}>
        {projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</Select></Field> : <p className="hint">Admins manage everyone and every project in this organization.</p>}
    </FormDialog>}
    {link && <Dialog title="Invitation ready" close={() => setLink("")}><div className="dialog-body">
      <Field label="Invitation link" hint="Share privately with the invited person. The link expires in 48 hours. Creating another link replaces this one.">
        <input readOnly value={link} onFocus={e => e.target.select()}/></Field>
      <button onClick={() => copy(link)}><Copy size={16}/>Copy invitation</button></div></Dialog>}
    {removing && <Confirm title="Remove member?" detail={removing.fullName + " will lose access to this organization immediately."} submit="Remove member"
      close={() => setRemoving(null)} onSave={async () => {await api(path + "/" + removing.userId, "DELETE"); refresh(); toast("Member removed");}}/>}
  </>;
}
