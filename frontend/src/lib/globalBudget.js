const COUNTED = new Set(['CONFIRMED', 'VERIFIED_QUOTE', 'MANUAL_ESTIMATE', 'MANUAL_FREE', 'VERIFIED_FREE']);

export function hotelBudgetExpenseId(tripId, lodging) {
  const property = `${lodging?.nombre || ''}|${lodging?.direccion || ''}`.normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('en').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 90);
  return `hotel:${tripId}:${property || 'pending'}`;
}

export function calculateGlobalBudget({ total, currency = 'COP', entries = [] }) {
  const seen = new Set();
  let confirmed = 0;
  let estimated = 0;
  let sandbox = 0;
  let unknown = 0;
  let foreignCurrency = 0;
  const byCategory = {};
  const included = [];
  const unknownEntries = [];
  const unconvertedEntries = [];

  for (const entry of Array.isArray(entries) ? entries : []) {
    if (!entry || entry.id == null || seen.has(String(entry.id))) continue;
    seen.add(String(entry.id));
    const amount = Number(entry.amount);
    if (entry.confidence === 'UNKNOWN' || !Number.isFinite(amount) || amount < 0) { unknown += 1; unknownEntries.push(entry); continue; }
    if (entry.confidence === 'SANDBOX') { sandbox += amount; continue; }
    if (!COUNTED.has(entry.confidence)) { unknown += 1; continue; }
    if (entry.currency !== currency) {
      const rate = Number(entry.rateToBudget);
      if (!Number.isFinite(rate) || rate <= 0) { foreignCurrency += 1; unconvertedEntries.push(entry); continue; }
      // A user-entered exchange rate is itself an estimate, never a verified conversion.
      const converted = amount * rate;
      estimated += converted;
      byCategory[entry.category] = (byCategory[entry.category] || 0) + converted;
      included.push({ ...entry, convertedAmount: converted, converted: true });
      continue;
    }
    if (entry.confidence === 'CONFIRMED' || entry.confidence === 'VERIFIED_QUOTE') confirmed += amount;
    else estimated += amount;
    byCategory[entry.category] = (byCategory[entry.category] || 0) + amount;
    included.push({ ...entry, convertedAmount: amount, converted: false });
  }

  const budget = Number(total);
  const hasBudget = total !== null && total !== undefined && total !== '' && Number.isFinite(budget) && budget >= 0;
  const spent = confirmed + estimated;
  return {
    currency, total: hasBudget ? budget : null, confirmed, estimated, sandbox, unknown, foreignCurrency, spent, byCategory, included,
    unknownEntries, unconvertedEntries,
    hasBudget,
    remaining: hasBudget ? budget - spent : null,
    overBudget: hasBudget && spent > budget,
    percentUsed: hasBudget && budget > 0 ? spent / budget * 100 : null,
  };
}
