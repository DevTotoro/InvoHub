<script lang="ts">
  import { goto, invalidate } from '$app/navigation';
  import { resolve } from '$app/paths';
  import type { CompanyRole } from '#lib/api/companies.ts';
  import { ASSIGNABLE_ROLES, changeMemberRole, leaveCompany, removeMember, type Member } from '#lib/api/members.ts';
  import { canManageMembers, roleName } from '#lib/company/permissions.ts';
  import { revokeInvitation, type Invitation } from '#lib/api/invitations.ts';
  import InviteForm from '#lib/components/InviteForm.svelte';
  import { confirmAction } from '#lib/components/confirm.svelte.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import NativeSelect from '#lib/components/ui/NativeSelect.svelte';
  import { formatDate } from '#lib/format/intl.ts';
  import { errorMessage } from '#lib/i18n/errors.ts';
  import { m } from '#lib/paraglide/messages.js';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  const canManage = $derived(canManageMembers(data.role, data.company));
  const roleOptions = ASSIGNABLE_ROLES.map((role) => ({ value: role, label: roleName(role) }));
  /** Where the user can invite to: the companies they manage, this one first */
  const invitable = $derived(
    data.user.companies
      .filter((c) => canManageMembers(c.role, c.company))
      .sort((a, b) => Number(b.company.id === data.company.id) - Number(a.company.id === data.company.id))
  );
  /** The owner's access and your own aren't managed here: the owner transfers ownership, you leave */
  const manageable = (member: Member) => canManage && member.role !== 'OWNER' && member.userId !== data.user.id;

  /** The member being changed or removed, or 'leave' while leaving */
  let busy: string | undefined = $state();
  let error: string | undefined = $state();

  async function run(key: string, action: () => Promise<unknown>) {
    busy = key;
    error = undefined;
    try {
      await action();
    } catch (e) {
      error = errorMessage(e);
      // Whatever failed, show the list as it really is now
      await invalidate('app:members');
    } finally {
      busy = undefined;
    }
  }

  function changeRole(member: Member, role: CompanyRole) {
    return run(member.userId, async () => {
      await changeMemberRole(data.company.id, member.userId, role);
      await invalidate('app:members');
    });
  }

  async function remove(member: Member) {
    if (
      !(await confirmAction(
        m.team_remove_confirm({ email: member.email, company: data.company.name }),
        m.team_remove()
      ))
    )
      return;
    return run(member.userId, async () => {
      await removeMember(data.company.id, member.userId);
      await invalidate('app:members');
    });
  }

  async function revoke(invitation: Invitation) {
    if (!(await confirmAction(m.team_revoke_confirm({ email: invitation.email }), m.team_revoke()))) return;
    return run(invitation.id, async () => {
      await revokeInvitation(data.company.id, invitation.id);
      await invalidate('app:members');
    });
  }

  async function leave() {
    if (!(await confirmAction(m.team_leave_confirm({ company: data.company.name }), m.team_leave()))) return;
    return run('leave', async () => {
      await leaveCompany(data.company.id);
      // The user's companies have changed; / opens another one, or onboarding
      await goto(resolve('/(app)'), { invalidateAll: true });
    });
  }
</script>

<svelte:head>
  <title>{m.team_title()} · {data.company.name} · InvoHub</title>
</svelte:head>

<main class="container mx-auto px-4 py-8">
  <h2 id="team-title" class="text-xl font-semibold">{m.team_title()}</h2>

  {#if error}
    <p class="mt-4 text-sm text-red-600" role="alert">{error}</p>
  {/if}

  <ul aria-labelledby="team-title" class="mt-4 divide-y divide-gray-200 border-y border-gray-200">
    {#each data.members as member (member.userId)}
      <li class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 py-3">
        <div class="min-w-0">
          <p class="truncate text-sm font-medium text-gray-900">
            {member.email}
            {#if member.userId === data.user.id}<span class="font-normal text-gray-500"> ({m.team_you()})</span>{/if}
          </p>
          <p class="text-sm text-gray-500">{m.team_since({ date: formatDate(member.since) })}</p>
        </div>

        <div class="flex items-center gap-2">
          {#if manageable(member)}
            <NativeSelect
              aria-label={m.team_role_of({ email: member.email })}
              value={member.role}
              options={roleOptions}
              disabled={busy === member.userId}
              onchange={(e) => changeRole(member, e.currentTarget.value as CompanyRole)}
              class="w-40"
            />
            <Button
              variant="ghost"
              aria-label={m.team_remove_of({ email: member.email })}
              disabled={busy === member.userId}
              onclick={() => remove(member)}
            >
              {m.team_remove()}
            </Button>
          {:else}
            <span class="text-sm text-gray-700">{roleName(member.role)}</span>
          {/if}
        </div>
      </li>
    {/each}
  </ul>

  {#if canManage}
    {#if data.invitations.length > 0}
      <h3 id="invitations-title" class="mt-8 font-semibold">{m.team_invitations()}</h3>
      <ul aria-labelledby="invitations-title" class="mt-2 divide-y divide-gray-200 border-y border-gray-200">
        {#each data.invitations as invitation (invitation.id)}
          <li class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 py-3">
            <div class="min-w-0">
              <p class="truncate text-sm font-medium text-gray-900">{invitation.email}</p>
              <p class="text-sm text-gray-500">
                {m.team_invitation_detail({
                  role: roleName(invitation.role),
                  email: invitation.invitedBy,
                  date: formatDate(invitation.expiresAt)
                })}
              </p>
            </div>
            <Button
              variant="ghost"
              aria-label={m.team_revoke_of({ email: invitation.email })}
              disabled={busy === invitation.id}
              onclick={() => revoke(invitation)}
            >
              {m.team_revoke()}
            </Button>
          </li>
        {/each}
      </ul>
    {/if}

    <div class="mt-8 max-w-md">
      <InviteForm companies={invitable} onsent={() => invalidate('app:members')} />
    </div>
  {/if}

  {#if data.role !== 'OWNER'}
    <Button variant="secondary" class="mt-6" loading={busy === 'leave'} onclick={leave}>{m.team_leave()}</Button>
  {/if}
</main>
