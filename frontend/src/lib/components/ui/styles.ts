/** Visual styles shared by Button and Link, so a link styled as a button looks exactly like one. */
export type Variant = 'primary' | 'secondary' | 'ghost' | 'link' | 'icon';

const base =
  'inline-flex items-center justify-center gap-2 text-sm font-medium transition-colors ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 ' +
  'disabled:cursor-not-allowed disabled:opacity-60';

const variants: Record<Variant, string> = {
  primary: 'rounded-lg bg-indigo-600 px-4 py-2 text-white hover:bg-indigo-500',
  secondary: 'rounded-lg border border-gray-300 bg-white px-4 py-2 text-gray-900 hover:bg-gray-50',
  ghost: 'rounded-lg px-3 py-2 text-gray-700 hover:bg-gray-100 hover:text-gray-900',
  link: 'rounded-sm text-indigo-600 hover:text-indigo-500 hover:underline',
  // Low-key icon actions: muted until hovered; the padding enlarges the click target, not the icon
  icon: 'rounded-sm p-1.5 text-gray-400 hover:text-gray-900'
};

export function variantClasses(variant: Variant): string {
  return `${base} ${variants[variant]}`;
}
