<script lang="ts">
  import { goto, invalidateAll } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { acceptInvitation, declineInvitation, type Invitation } from '#lib/api/invitations.ts';
  import { rememberCompany } from '#lib/company/last-company.ts';
  import { roleName } from '#lib/company/permissions.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import { formatDate } from '#lib/format/intl.ts';
  import { errorMessage } from '#lib/i18n/errors.ts';
  import { m } from '#lib/paraglide/messages.js';
  import type { PageProps } from './$types';

  let { data }: PageProps = $props();

  /** The invitation being accepted or declined */
  let busy: string | undefined = $state();
  let error: string | undefined = $state();

  async function accept(invitation: Invitation) {
    busy = invitation.id;
    error = undefined;
    try {
      const joined = await acceptInvitation(invitation.id);
      rememberCompany(joined.company.id);
      // invalidateAll: the user's companies and invitations have changed
      await goto(resolve('/(app)/c/[companyId]', { companyId: joined.company.id }), { invalidateAll: true });
    } catch (e) {
      error = errorMessage(e);
      await invalidateAll();
    } finally {
      busy = undefined;
    }
  }

  async function decline(invitation: Invitation) {
    busy = invitation.id;
    error = undefined;
    try {
      await declineInvitation(invitation.id);
    } catch (e) {
      error = errorMessage(e);
    } finally {
      await invalidateAll();
      busy = undefined;
    }
  }
</script>

<svelte:head>
  <title>{m.invitations_title()} · InvoHub</title>
</svelte:head>

<main class="flex justify-center px-4 py-16">
  <div class="w-full max-w-lg">
    <h2 id="invitations-title" class="text-xl font-semibold">{m.invitations_title()}</h2>

    {#if error}
      <p class="mt-4 text-sm text-red-600" role="alert">{error}</p>
    {/if}

    {#if data.user.invitations.length === 0}
      <p class="mt-4 text-sm text-gray-600">{m.invitations_none()}</p>
    {:else}
      <ul aria-labelledby="invitations-title" class="mt-4 divide-y divide-gray-200 border-y border-gray-200">
        {#each data.user.invitations as invitation (invitation.id)}
          <li class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 py-3">
            <div class="min-w-0">
              <p class="truncate font-medium text-gray-900">{invitation.company.name}</p>
              <p class="text-sm text-gray-600">
                {m.invitations_invited_by({ email: invitation.invitedBy, role: roleName(invitation.role) })}
              </p>
              <p class="text-sm text-gray-500">{m.invitations_expires({ date: formatDate(invitation.expiresAt) })}</p>
            </div>
            <div class="flex items-center gap-2">
              <Button loading={busy === invitation.id} disabled={!!busy} onclick={() => accept(invitation)}>
                {m.invitations_accept()}
              </Button>
              <Button variant="ghost" disabled={!!busy} onclick={() => decline(invitation)}>
                {m.invitations_decline()}
              </Button>
            </div>
          </li>
        {/each}
      </ul>
    {/if}
  </div>
</main>
