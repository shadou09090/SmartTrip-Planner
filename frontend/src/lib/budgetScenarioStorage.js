// Keep v1 keys/records compatible; the new optional pricing metadata does not require a migration.
const VERSION = 1;
const ALLOWED_CONFIDENCE = new Set(['CONFIRMED', 'VERIFIED_QUOTE', 'MANUAL_ESTIMATE', 'MANUAL_FREE', 'VERIFIED_FREE', 'SANDBOX', 'UNKNOWN']);
const ALLOWED_EXPENSE_FIELDS = new Set(['id', 'category', 'label', 'amount', 'currency', 'confidence', 'rateToBudget', 'rateSource', 'rateDate', 'unitAmount', 'pricingMode', 'travelers', 'nights', 'rooms', 'source', 'queriedAt', 'availability']);

export function budgetScenarioStorageKey(userId, tripId) {
  if (userId == null || tripId == null || String(userId).trim() === '' || String(tripId).trim() === '') return null;
  return `smarttrip.budget-scenario.v${VERSION}:${encodeURIComponent(String(userId))}:${encodeURIComponent(String(tripId))}`;
}

export function readBudgetScenario(storage, key) {
  if (!storage || !key) return { expenses: [], status: 'unavailable' };
  let raw;
  try { raw = storage.getItem(key); } catch { return { expenses: [], status: 'unavailable' }; }
  if (raw == null) return { expenses: [], status: 'ready' };
  try {
    const value = JSON.parse(raw);
    if (![1, VERSION].includes(value?.version) || !Array.isArray(value.expenses) || !value.expenses.every(isValidExpense)) {
      return { expenses: [], status: 'corrupt' };
    }
    const unique = [...new Map(value.expenses.map((expense) => [expense.id, expense])).values()];
    const result = value.version !== VERSION ? { expenses: unique, status: 'ready', migrated: true }
      : { expenses: unique, status: 'ready' };
    if (Number.isInteger(value.travelers) && value.travelers >= 1 && value.travelers <= 20) result.travelers = value.travelers;
    return result;
  } catch {
    return { expenses: [], status: 'corrupt' };
  }
}

export function writeBudgetScenario(storage, key, expenses, travelers = undefined) {
  if (!storage || !key || !Array.isArray(expenses) || !expenses.every(isValidExpense)) return false;
  if (travelers !== undefined && (!Number.isInteger(Number(travelers)) || Number(travelers) < 1 || Number(travelers) > 20)) return false;
  try {
    storage.setItem(key, JSON.stringify({ version: VERSION, expenses, ...(travelers === undefined ? {} : { travelers: Number(travelers) }) }));
    return true;
  } catch { return false; }
}

export function clearBudgetScenario(storage, key) {
  if (!storage || !key) return false;
  try { storage.removeItem(key); return true; } catch { return false; }
}

function isValidExpense(expense) {
  const unknown = expense?.confidence === 'UNKNOWN';
  return expense && typeof expense.id === 'string' && expense.id.length <= 160
    && Object.keys(expense).every((field) => ALLOWED_EXPENSE_FIELDS.has(field))
    && typeof expense.label === 'string' && expense.label.trim().length > 0 && expense.label.length <= 120
    && typeof expense.category === 'string'
    && (unknown ? expense.amount == null : Number.isFinite(Number(expense.amount)) && Number(expense.amount) >= 0)
    && (unknown ? expense.currency == null : /^[A-Z]{3}$/.test(expense.currency || ''))
    && ALLOWED_CONFIDENCE.has(expense.confidence)
    && (expense.rateToBudget == null || (Number.isFinite(Number(expense.rateToBudget)) && Number(expense.rateToBudget) > 0))
    && (expense.unitAmount == null || (Number.isFinite(Number(expense.unitAmount)) && Number(expense.unitAmount) >= 0))
    && (expense.pricingMode == null || ['total', 'per_person', 'daily_person', 'per_night'].includes(expense.pricingMode))
    && (expense.source == null || (typeof expense.source === 'string' && expense.source.length <= 120))
    && (expense.rateSource == null || (typeof expense.rateSource === 'string' && expense.rateSource.length <= 120))
    && (expense.rateDate == null || (typeof expense.rateDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(expense.rateDate)))
    && (expense.queriedAt == null || (typeof expense.queriedAt === 'string' && Number.isFinite(Date.parse(expense.queriedAt))))
    && (expense.availability == null || ['available', 'unknown', 'expired', 'not_provided'].includes(expense.availability))
    && ['travelers', 'nights', 'rooms'].every((field) => expense[field] == null || (Number.isInteger(Number(expense[field])) && Number(expense[field]) >= 0));
}
