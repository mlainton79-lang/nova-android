(function inspect() {
  "use strict";

  // ── Configuration ──────────────────────────────────────────────
  const VALUE_PREVIEW_MAX = 30;
  const NEARBY_TEXT_MAX = 120;
  const SENSITIVE_TYPES = ["password", "email", "tel", "hidden"];
  const SENSITIVE_NAME_HINTS = ["token", "secret", "password", "auth", "session"];
  const DANGEROUS_LABEL_PATTERNS = [
    /save\s*draft/i,
    /^publish$/i,
    /post\s*item/i,
    /list\s*now/i,
    /upload/i
  ];

  // ── Helpers ────────────────────────────────────────────────────
  function isVisible(el) {
    if (!el || !el.getBoundingClientRect) return false;
    const rect = el.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return false;
    const style = window.getComputedStyle(el);
    if (style.visibility === "hidden" || style.display === "none") return false;
    if (parseFloat(style.opacity) === 0) return false;
    return true;
  }

  function clamp(text, max) {
    if (text == null) return "";
    const s = String(text).replace(/\s+/g, " ").trim();
    return s.length > max ? s.slice(0, max) + "…" : s;
  }

  function isSensitive(el) {
    const type = (el.getAttribute && el.getAttribute("type")) || "";
    if (SENSITIVE_TYPES.indexOf(type.toLowerCase()) !== -1) return true;
    const name = (el.getAttribute && el.getAttribute("name")) || "";
    const id = el.id || "";
    const haystack = (name + " " + id).toLowerCase();
    return SENSITIVE_NAME_HINTS.some(function (hint) {
      return haystack.indexOf(hint) !== -1;
    });
  }

  function getLabelText(el) {
    // Priority: aria-label → <label for=id> → closest label parent →
    //           placeholder → name → data-testid
    const aria = el.getAttribute && el.getAttribute("aria-label");
    if (aria) return clamp(aria, 80);

    if (el.id) {
      const lbl = document.querySelector('label[for="' + el.id + '"]');
      if (lbl && lbl.textContent) return clamp(lbl.textContent, 80);
    }

    const closestLabel = el.closest && el.closest("label");
    if (closestLabel && closestLabel.textContent) {
      return clamp(closestLabel.textContent, 80);
    }

    const placeholder = el.getAttribute && el.getAttribute("placeholder");
    if (placeholder) return clamp(placeholder, 80);

    const name = el.getAttribute && el.getAttribute("name");
    if (name) return name;

    const testid = el.getAttribute && el.getAttribute("data-testid");
    if (testid) return testid;

    return "";
  }

  function getNearbyText(el) {
    // Walk up two parents and grab visible text. Useful when label
    // priority chain comes up empty.
    let node = el.parentElement;
    let depth = 0;
    while (node && depth < 2) {
      const text = node.textContent || "";
      const trimmed = text.replace(/\s+/g, " ").trim();
      if (trimmed.length >= 3) return clamp(trimmed, NEARBY_TEXT_MAX);
      node = node.parentElement;
      depth++;
    }
    return "";
  }

  function cssPath(el) {
    if (!el || el.nodeType !== 1) return "";
    const parts = [];
    let node = el;
    while (node && node.nodeType === 1 && parts.length < 6) {
      let segment = node.tagName.toLowerCase();
      if (node.id) {
        segment += "#" + node.id;
        parts.unshift(segment);
        break;
      }
      const parent = node.parentElement;
      if (parent) {
        const siblings = Array.prototype.filter.call(
          parent.children,
          function (c) { return c.tagName === node.tagName; }
        );
        if (siblings.length > 1) {
          const idx = siblings.indexOf(node) + 1;
          segment += ":nth-of-type(" + idx + ")";
        }
      }
      parts.unshift(segment);
      node = parent;
    }
    return parts.join(" > ");
  }

  function isUniqueSelector(selector) {
    try {
      return document.querySelectorAll(selector).length === 1;
    } catch (e) {
      return false;
    }
  }

  function buildSelectorCandidates(el) {
    const accepted = [];
    const rejected = [];

    function attempt(selector, reasonIfReject) {
      if (!selector) return;
      try {
        const count = document.querySelectorAll(selector).length;
        if (count === 1) {
          accepted.push(selector);
        } else {
          rejected.push({ selector: selector, reason: "matched " + count + " elements" });
        }
      } catch (e) {
        rejected.push({ selector: selector, reason: "invalid: " + e.message });
      }
    }

    // Priority 1: data-testid (designed to be stable)
    const testid = el.getAttribute("data-testid");
    if (testid) attempt('[data-testid="' + testid + '"]');

    // Priority 2: name attribute
    const name = el.getAttribute("name");
    if (name) {
      const tag = el.tagName.toLowerCase();
      attempt(tag + '[name="' + name + '"]');
    }

    // Priority 3: aria-label exact match
    const aria = el.getAttribute("aria-label");
    if (aria) {
      const tag = el.tagName.toLowerCase();
      attempt(tag + '[aria-label="' + aria.replace(/"/g, '\\"') + '"]');
    }

    // Priority 4: placeholder exact match
    const placeholder = el.getAttribute("placeholder");
    if (placeholder) {
      const tag = el.tagName.toLowerCase();
      attempt(tag + '[placeholder="' + placeholder.replace(/"/g, '\\"') + '"]');
    }

    // Priority 5: id (stable in this app, but Vinted may auto-generate)
    if (el.id) attempt("#" + el.id);

    // Priority 6: cssPath fallback (last resort, fragile)
    const path = cssPath(el);
    if (path) attempt(path);

    return { accepted: accepted, rejected: rejected };
  }

  function isDangerousLabel(text) {
    if (!text) return false;
    return DANGEROUS_LABEL_PATTERNS.some(function (p) { return p.test(text); });
  }

  // ── Element descriptor builder ─────────────────────────────────
  function describe(el, index) {
    const tag = el.tagName.toLowerCase();
    const type = (el.getAttribute("type") || "").toLowerCase();
    const labelText = getLabelText(el);
    const visible = isVisible(el);
    const sensitive = isSensitive(el);

    // Value preview — guarded
    let valueLength = 0;
    let valuePreview = "";
    if (visible && !sensitive && typeof el.value === "string") {
      valueLength = el.value.length;
      if (valueLength > 0) {
        valuePreview = clamp(el.value, VALUE_PREVIEW_MAX);
      }
    }

    const rect = el.getBoundingClientRect();
    const candidates = buildSelectorCandidates(el);

    const dangerousByLabel = isDangerousLabel(labelText) || isDangerousLabel(el.textContent);
    const dangerReason = dangerousByLabel ? "publish_or_save_or_upload_label" : null;

    return {
      index: index,
      tag: tag,
      type: type,
      name: el.getAttribute("name") || "",
      id: el.id || "",
      dataTestId: el.getAttribute("data-testid") || "",
      ariaLabel: el.getAttribute("aria-label") || "",
      placeholder: el.getAttribute("placeholder") || "",
      autocomplete: el.getAttribute("autocomplete") || "",
      role: el.getAttribute("role") || "",
      ariaHasPopup: el.getAttribute("aria-haspopup") || "",
      contentEditable: el.isContentEditable === true,
      required: el.required === true || el.getAttribute("aria-required") === "true",
      disabled: el.disabled === true,
      readonly: el.readOnly === true,
      visible: visible,
      sensitive: sensitive,
      valueLength: valueLength,
      valuePreview: valuePreview,
      labelText: labelText,
      nearbyText: getNearbyText(el),
      rect: {
        x: Math.round(rect.x),
        y: Math.round(rect.y),
        w: Math.round(rect.width),
        h: Math.round(rect.height)
      },
      stableSelectorCandidates: candidates.accepted,
      rejectedSelectorCandidates: candidates.rejected,
      dangerous: dangerousByLabel,
      dangerReason: dangerReason
    };
  }

  // ── Page guard ─────────────────────────────────────────────────
  function pageLooksLikeSellForm() {
    const url = location.href.toLowerCase();
    const title = (document.title || "").toLowerCase();
    if (url.indexOf("/items/new") !== -1) return true;
    if (title.indexOf("sell") !== -1) return true;
    // Also check for a heading that says Sell:
    const headings = document.querySelectorAll("h1, h2");
    for (let i = 0; i < headings.length; i++) {
      if ((headings[i].textContent || "").toLowerCase().indexOf("sell") !== -1) {
        return true;
      }
    }
    return false;
  }

  // ── Root scoring ───────────────────────────────────────────────
  const INTERESTING_SELECTOR = [
    'input',
    'textarea',
    'select',
    'button',
    '[role="button"]',
    '[contenteditable="true"]',
    '[aria-haspopup]',
    'input[type="file"]'
  ].join(',');

  function scoreRoot(el) {
    const all = Array.from(el.querySelectorAll(INTERESTING_SELECTOR));
    let score = 0;
    let visibleCount = 0;
    let inputCount = 0;
    let textareaCount = 0;
    let fileCount = 0;
    let buttonCount = 0;

    for (let i = 0; i < all.length; i++) {
      const node = all[i];
      if (!isVisible(node)) continue;
      visibleCount++;

      const tag = node.tagName.toLowerCase();
      const type = (node.getAttribute('type') || '').toLowerCase();

      if (tag === 'input' && type === 'file') {
        fileCount++;
        score += 4;
      } else if (tag === 'input') {
        inputCount++;
        score += 3;
      } else if (tag === 'textarea') {
        textareaCount++;
        score += 3;
      } else if (tag === 'select') {
        score += 3;
      } else if (tag === 'button' || node.getAttribute('role') === 'button') {
        buttonCount++;
        score += 1;
      } else {
        score += 1;
      }
    }

    // Text-evidence bonus: subtree that mentions sell-form keywords
    // is more likely to BE the sell form.
    const text = (el.innerText || '').toLowerCase();
    if (text.indexOf('title') !== -1) score += 3;
    if (text.indexOf('description') !== -1) score += 3;
    if (text.indexOf('price') !== -1) score += 3;
    if (text.indexOf('category') !== -1) score += 2;
    if (text.indexOf('upload') !== -1) score += 2;
    if (text.indexOf('sell') !== -1) score += 2;

    return {
      score: score,
      visibleCount: visibleCount,
      inputCount: inputCount,
      textareaCount: textareaCount,
      fileCount: fileCount,
      buttonCount: buttonCount
    };
  }

  function pickRoot() {
    const selectors = [
      'main',
      '[data-testid*="sell" i]',
      '[data-testid*="item" i]',
      '[data-testid*="listing" i]',
      '[data-testid*="upload" i]',
      'form',
      'body'
    ];

    const candidates = [];

    for (let i = 0; i < selectors.length; i++) {
      const selector = selectors[i];
      let nodes;
      if (selector === 'body') {
        nodes = document.body ? [document.body] : [];
      } else {
        try {
          nodes = Array.prototype.slice.call(document.querySelectorAll(selector));
        } catch (e) {
          nodes = [];
        }
      }

      for (let j = 0; j < nodes.length; j++) {
        const el = nodes[j];
        if (!el) continue;
        const metrics = scoreRoot(el);
        candidates.push({
          selector: selector,
          el: el,
          score: metrics.score,
          visibleCount: metrics.visibleCount,
          inputCount: metrics.inputCount,
          textareaCount: metrics.textareaCount,
          fileCount: metrics.fileCount,
          buttonCount: metrics.buttonCount
        });
      }
    }

    candidates.sort(function (a, b) { return b.score - a.score; });

    const best = candidates[0] || {
      selector: 'body',
      el: document.body,
      score: 0,
      visibleCount: 0,
      inputCount: 0,
      textareaCount: 0,
      fileCount: 0,
      buttonCount: 0
    };

    // Build serialisable summary of candidates — STRIP el (DOM node)
    // because it cannot be JSON.stringify'd safely (circular refs,
    // would either throw or produce useless output).
    const candidatesSummary = candidates.slice(0, 8).map(function (c) {
      return {
        selector: c.selector,
        score: c.score,
        visibleCount: c.visibleCount,
        inputCount: c.inputCount,
        textareaCount: c.textareaCount,
        fileCount: c.fileCount,
        buttonCount: c.buttonCount
      };
    });

    return {
      selector: best.selector,
      el: best.el,
      score: best.score,
      visibleCount: best.visibleCount,
      candidates: candidatesSummary
    };
  }

  // ── Main inspection ────────────────────────────────────────────
  const root = pickRoot();

  const fieldSelectors = [
    "input",
    "textarea",
    "select",
    '[contenteditable="true"]'
  ];
  const buttonSelectors = [
    "button",
    '[role="button"]',
    '[aria-haspopup]'
  ];
  const fileInputSelectors = ['input[type="file"]'];

  function collect(selectors) {
    const results = [];
    selectors.forEach(function (sel) {
      const list = root.el.querySelectorAll(sel);
      for (let i = 0; i < list.length; i++) {
        if (results.indexOf(list[i]) === -1) results.push(list[i]);
      }
    });
    return results;
  }

  const rawFields = collect(fieldSelectors);
  const rawButtons = collect(buttonSelectors);
  const rawFileInputs = collect(fileInputSelectors);

  let hiddenSkipped = 0;
  function partition(elements) {
    const visible = [];
    elements.forEach(function (el) {
      if (isVisible(el)) {
        visible.push(el);
      } else {
        hiddenSkipped++;
      }
    });
    return visible;
  }

  const visibleFields = partition(rawFields);
  const visibleButtons = partition(rawButtons);
  const visibleFileInputs = rawFileInputs; // file inputs are often hidden by design

  const fields = visibleFields.map(function (el, i) { return describe(el, i); });
  const buttons = visibleButtons.map(function (el, i) { return describe(el, i); });
  const fileInputs = visibleFileInputs.map(function (el, i) { return describe(el, i); });

  // ── Likely-fields inference (read-only) ────────────────────────
  function findLikely(fields, patterns) {
    for (let i = 0; i < fields.length; i++) {
      const f = fields[i];
      if (!f.visible || f.disabled || f.readonly) continue;
      const haystack = [
        f.name, f.dataTestId, f.ariaLabel, f.placeholder, f.labelText, f.id
      ].join(" ").toLowerCase();
      for (let j = 0; j < patterns.length; j++) {
        if (patterns[j].test(haystack)) {
          return { selector: f.stableSelectorCandidates[0] || null, fieldIndex: i, reason: "matched " + patterns[j] };
        }
      }
    }
    return null;
  }

  const likelyFields = {
    title: findLikely(fields, [/title/, /what.*selling/i, /name.*item/i]),
    description: findLikely(fields, [/description/, /tell.*more/i, /details/]),
    price: findLikely(fields, [/price/, /amount/, /cost/])
  };

  // ── Sell-form confidence assessment ────────────────────────────
  const evidence = [];
  if (location.href.toLowerCase().indexOf('/items/new') !== -1) {
    evidence.push('url contains /items/new');
  }
  if ((document.title || '').toLowerCase().indexOf('sell') !== -1) {
    evidence.push('title contains sell');
  }
  // Check for sell-related h1/h2:
  const headings = document.querySelectorAll('h1, h2');
  for (let i = 0; i < headings.length; i++) {
    const t = (headings[i].textContent || '').toLowerCase();
    if (t.indexOf('sell') !== -1) {
      evidence.push('heading contains sell');
      break;
    }
  }
  const formControlsFound = fields.length > 0 || fileInputs.length > 0;
  if (formControlsFound) {
    evidence.push('root contains form controls');
  }

  let sellFormConfidence;
  if (formControlsFound && evidence.length >= 2) {
    sellFormConfidence = 'high';
  } else if (evidence.length >= 1) {
    sellFormConfidence = 'url_only';
  } else {
    sellFormConfidence = 'none';
  }

  // ── Diagnostics ────────────────────────────────────────────────
  // Shadow-host count: tells us if the page uses Shadow DOM. If >0
  // and we're missing controls, traversal-into-shadow is the next
  // suspect. We do NOT traverse Shadow DOM in this brick.
  let shadowHostCount = 0;
  try {
    const everything = document.querySelectorAll('*');
    for (let i = 0; i < everything.length; i++) {
      if (everything[i].shadowRoot) shadowHostCount++;
    }
  } catch (e) {
    shadowHostCount = -1;
  }

  // Hidden sample: the first 3 hidden elements we skipped, with
  // tag/type/name/id only. NEVER capture values, even from hidden
  // fields (could be CSRF tokens or session data).
  const hiddenSample = [];
  function sampleHidden(elements) {
    elements.forEach(function (el) {
      if (hiddenSample.length >= 3) return;
      if (isVisible(el)) return;
      hiddenSample.push({
        tag: el.tagName.toLowerCase(),
        type: (el.getAttribute('type') || '').toLowerCase(),
        name: el.getAttribute('name') || '',
        id: el.id || ''
      });
    });
  }
  sampleHidden(rawFields);
  sampleHidden(rawButtons);

  // ── Output ─────────────────────────────────────────────────────
  const payload = {
    phase: "3B.4",
    url: location.href,
    title: document.title,
    timestamp: new Date().toISOString(),
    pageLooksLikeSellForm: pageLooksLikeSellForm(),
    sellFormConfidence: sellFormConfidence,
    sellFormEvidence: evidence,
    formControlsFound: formControlsFound,
    rootSelectorUsed: root.selector,
    rootScore: root.score,
    rootVisibleElementCount: root.visibleCount,
    rootCandidates: root.candidates,
    counts: {
      fields: fields.length,
      buttons: buttons.length,
      fileInputs: fileInputs.length,
      hiddenSkipped: hiddenSkipped
    },
    shadowHostCount: shadowHostCount,
    hiddenSample: hiddenSample,
    likelyFields: likelyFields,
    fields: fields,
    buttons: buttons,
    fileInputs: fileInputs
  };

  return JSON.stringify(payload);
})();
