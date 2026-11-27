export const NAME_MAX_LENGTH = 100;

export function validateName(value: string, label: string): string | undefined {
  const trimmed = value.trim();
  if (!trimmed) return `Please enter your ${label.toLowerCase()}.`;
  if (trimmed.length > NAME_MAX_LENGTH) return `${label} must be 100 characters or fewer.`;
  return undefined;
}
