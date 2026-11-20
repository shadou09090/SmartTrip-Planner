import test from 'node:test';
import assert from 'node:assert/strict';
import { formatHotelPrice, freshHotelLivePrices, isHotelQuoteFresh, normalizeHotelLivePrices } from './hotelLivePrices.js';

test('normalizes documented OBO live stay totals and keeps attribution-safe booking URLs', () => {
  const offers = normalizeHotelLivePrices({ currency: 'EUR', retrievedAt: '2026-11-15T12:00:00Z', results: {
    hotels: [{ hotel_token: 'opaque-1', hotel_name: 'Hotel Example', address: 'Centre', total_price: { amount: '480', currency: 'EUR' },
      per_night: { amount: '160', currency: 'EUR' }, taxes_included: true, fees_included: false,
      booking_url: 'https://onebookingonline.com/hotel/example?check_in=2026-12-01' }],
  } });
  assert.equal(offers.length, 1);
  assert.equal(offers[0].total, 480);
  assert.equal(offers[0].nightly, 160);
  assert.equal(offers[0].currency, 'EUR');
  assert.equal(offers[0].bookingUrl.startsWith('https://onebookingonline.com/'), true);
});

test('keeps priced results while rejecting unpriced rows and unsafe booking links', () => {
  const offers = normalizeHotelLivePrices({ results: { hotels: [
    { name: 'Unpriced', booking_url: 'https://onebookingonline.com/hotel/a' },
    { name: 'Untrusted', total_price: 12, currency: 'COP', booking_url: 'https://other.example/checkout' },
  ] } });
  assert.equal(offers.length, 1);
  assert.equal(offers[0].name, 'Untrusted');
  assert.equal(offers[0].bookingUrl, null);
  assert.equal(normalizeHotelLivePrices({ hotels: [{ name: 'No money', total_price: 1, currency: 'US' }] }).length, 0);
});

test('enforces the provider 30-minute live price freshness window', () => {
  const now = Date.parse('2026-11-15T12:30:00Z');
  assert.equal(isHotelQuoteFresh('2026-11-15T12:00:01Z', now), true);
  assert.equal(isHotelQuoteFresh('2026-11-15T12:00:00Z', now), false);
  assert.equal(isHotelQuoteFresh('not-a-date', now), false);
});

test('formats returned price safely if a provider currency cannot be rendered by Intl', () => {
  assert.match(formatHotelPrice(125, 'NOT-A-CURRENCY'), /125/);
});

test('does not return stale live prices for display after the provider freshness window', () => {
  const response = { retrievedAt: '2026-11-15T12:00:00Z', results: { hotels: [
    { name: 'Stale', total_price: { amount: 100, currency: 'EUR' } },
    { name: 'Fresh', total_price: { amount: 200, currency: 'EUR' }, freshness: { quoted_at: '2026-11-15T12:20:00Z' } },
  ] } };
  const offers = freshHotelLivePrices(response, Date.parse('2026-11-15T12:30:00Z'));
  assert.deepEqual(offers.map((offer) => offer.name), ['Fresh']);
});
