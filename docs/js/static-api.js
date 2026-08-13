(() => {
  const cache = new Map();

  async function load(name) {
    if (!cache.has(name)) {
      cache.set(name, fetch(`static-api/${name}`).then(response => {
        if (!response.ok) throw new Error(`Unable to load static dataset: ${name}`);
        return response.json();
      }));
    }
    return cache.get(name);
  }

  window.staticAnalysisApi = {
    async tagYearly(tag, years) {
      const data = await load('tag-trends.json');
      const rows = data.yearly.filter(row => row.tag === String(tag).trim().toLowerCase());
      return rows.slice(-Math.max(1, Number(years) || rows.length));
    },
    async tagFeatures(tags) {
      const data = await load('tag-trends.json');
      const wanted = new Set(tags.map(tag => String(tag).trim().toLowerCase()));
      return data.features.filter(row => wanted.has(row.tag));
    },
    async popularTags(limit) {
      const data = await load('tag-trends.json');
      return data.features.slice(0, Math.max(1, Number(limit) || 20));
    },
    async cooccurrence(limit) {
      const data = await load('cooccurrence.json');
      return data.slice(0, Math.max(1, Number(limit) || 20));
    },
    async pitfalls(limit, hasCode) {
      const data = await load('pitfalls.json');
      const filtered = hasCode === '' ? data : data.filter(item => hasCode === 'true' ? Boolean(item.exampleCode) : !item.exampleCode);
      return filtered.slice(0, Math.max(1, Number(limit) || 10));
    },
    solvability() {
      return load('solvability.json');
    }
  };
})();
