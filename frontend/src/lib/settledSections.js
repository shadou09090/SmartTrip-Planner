/** Keep successful sections available when an unrelated endpoint fails. */
export function partitionSettledResults(names, results) {
  const fulfilled = {};
  const rejected = {};
  names.forEach((name, index) => {
    const result = results[index];
    if (result?.status === 'fulfilled') fulfilled[name] = result.value;
    else if (result?.status === 'rejected') rejected[name] = result.reason;
  });
  return { fulfilled, rejected };
}
