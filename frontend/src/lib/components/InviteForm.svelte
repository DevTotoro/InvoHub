<script lang="ts">
  import type { CompanyRole, UserCompany } from '#lib/api/companies.ts';
  import { graphqlProblem } from '#lib/api/graphql.ts';
  import { inviteMembers } from '#lib/api/invitations.ts';
  import { ASSIGNABLE_ROLES } from '#lib/api/members.ts';
  import { roleName } from '#lib/company/permissions.ts';
  import Button from '#lib/components/ui/Button.svelte';
  import Input from '#lib/components/ui/Input.svelte';
  import NativeSelect from '#lib/components/ui/NativeSelect.svelte';
  import { errorMessage } from '#lib/i18n/errors.ts';
  import { m } from '#lib/paraglide/messages.js';

  interface Props {
    /** The companies the user can invite to (owner or admin, active); the first is preselected */
    companies: UserCompany[];
    /** After invitations are sent */
    onsent?: () => void;
  }

  let { companies, onsent }: Props = $props();

  const formId = $props.id();
  const roleOptions = ASSIGNABLE_ROLES.map((role) => ({ value: role, label: roleName(role) }));
  let email = $state('');
  // Which companies to invite to, with the role for each; the current company is ticked to begin with
  let picked: Record<string, boolean> = $state({});
  let roles: Record<string, CompanyRole> = $state({});
  $effect.pre(() => {
    for (const [i, { company }] of companies.entries()) {
      picked[company.id] ??= i === 0;
      roles[company.id] ??= 'MEMBER';
    }
  });

  let sending = $state(false);
  let emailError: string | undefined = $state();
  let formError: string | undefined = $state();
  let sent: string | undefined = $state();

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    emailError = formError = sent = undefined;
    const chosen = companies.filter(({ company }) => picked[company.id]);
    if (chosen.length === 0) {
      formError = m.invite_pick_company();
      return;
    }

    sending = true;
    try {
      await inviteMembers(
        email,
        chosen.map(({ company }) => ({ companyId: company.id, role: roles[company.id] }))
      );
      sent = m.invite_sent({ email: email.trim() });
      email = '';
      onsent?.();
    } catch (e) {
      const problem = graphqlProblem(e);
      if (problem?.code === 'invalid_input') {
        emailError = m.invite_invalid_email();
      } else if (problem?.code === 'email_delivery_failed') {
        formError = m.error_email_delivery_failed_invite();
      } else {
        formError = refusal(e) ?? errorMessage(e);
      }
    } finally {
      sending = false;
    }
  }

  /** Why an invitation was refused, naming the company it is about */
  function refusal(error: unknown): string | undefined {
    const problem = graphqlProblem(error);
    if (problem?.code !== 'invitation_refused') return undefined;
    const company = companies.find((c) => c.company.id === problem.companyId)?.company.name ?? '';
    const who = email.trim();
    switch (problem.reason) {
      case 'already_member':
        return m.invite_already_member({ email: who, company });
      case 'already_invited':
        return m.invite_already_invited({ email: who, company });
      case 'recipient_daily_limit':
        return m.invite_recipient_daily_limit({ email: who });
      case 'daily_limit':
        return m.invite_daily_limit();
      default:
        return undefined;
    }
  }
</script>

<form class="space-y-4" onsubmit={submit} aria-labelledby="{formId}-title">
  <h3 id="{formId}-title" class="font-semibold">{m.invite_title()}</h3>

  <Input
    label={m.invite_email()}
    type="email"
    name="email"
    required
    autocomplete="off"
    bind:value={email}
    error={emailError}
  />

  <fieldset>
    <legend class="block text-sm font-medium text-gray-900">{m.invite_companies()}</legend>
    <ul class="mt-1 space-y-2">
      {#each companies as { company } (company.id)}
        <li class="flex flex-wrap items-center justify-between gap-2">
          <label class="flex min-w-0 items-center gap-2 text-sm text-gray-900">
            <input type="checkbox" bind:checked={picked[company.id]} class="size-4 accent-taupe-600" />
            <span class="truncate">{company.name}</span>
          </label>
          <NativeSelect
            aria-label={m.invite_role_for({ company: company.name })}
            bind:value={roles[company.id]}
            options={roleOptions}
            disabled={!picked[company.id]}
            class="w-40"
          />
        </li>
      {/each}
    </ul>
  </fieldset>

  {#if formError}
    <p class="text-sm text-red-600" role="alert">{formError}</p>
  {/if}
  {#if sent}
    <p class="text-sm text-green-700" role="status">{sent}</p>
  {/if}

  <Button type="submit" loading={sending}>{sending ? m.invite_sending() : m.invite_submit()}</Button>
</form>
