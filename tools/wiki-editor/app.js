(function () {
  "use strict";

  const PAGE_ID = /^[a-z0-9][a-z0-9_-]{0,63}$/;
  const IMAGE_NAME = /^[a-z0-9][a-z0-9_-]{0,95}$/;
  const TILE_SIZE = 256;
  const FIRST_GLYPH = 0xe010;
  const DEFAULT_BACKGROUND = "<font:guidelegowelt:wiki>\uE000</font>";
  const COLORS = {
    gold: "#ffaa00", yellow: "#ffff55", green: "#55ff55", aqua: "#55ffff",
    red: "#ff5555", white: "#ffffff", gray: "#aaaaaa", blue: "#5555ff",
    dark_red: "#aa0000", dark_green: "#00aa00", dark_aqua: "#00aaaa"
  };
  const state = {
    data: null, font: null, images: { textures: [], sourceImages: [] }, yamlHeader: "",
    selectedId: null, view: "page", previewMode: "page", issues: [], history: [], future: [],
    lastHistoryKey: "", lastHistoryAt: 0, dirty: false, saving: false, saveTimer: 0, importImage: null
  };
  const nodes = {};
  const $ = (id) => document.getElementById(id);

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function icon(name) {
    const node = document.createElement("i");
    node.dataset.lucide = name;
    return node;
  }

  function iconButton(name, label, className = "icon-button") {
    const button = el("button", className);
    button.type = "button";
    button.title = label;
    button.setAttribute("aria-label", label);
    button.append(icon(name));
    return button;
  }

  function refreshIcons(root = document) {
    if (window.lucide) window.lucide.createIcons({ root });
  }

  async function requestText(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) throw new Error((await response.text()) || response.statusText);
    return response.text();
  }

  async function requestJson(url) {
    const response = await fetch(url, { cache: "no-store" });
    if (!response.ok) throw new Error(await response.text());
    return response.json();
  }

  const copy = (value) => JSON.parse(JSON.stringify(value));
  const entries = () => Object.entries(state.data?.pages || {});
  const page = () => state.data?.pages?.[state.selectedId] || null;
  const snapshot = () => ({ data: copy(state.data), font: copy(state.font), selectedId: state.selectedId });

  function recordHistory(key) {
    const now = Date.now();
    if (state.lastHistoryKey !== key || now - state.lastHistoryAt > 800) {
      state.history.push(snapshot());
      if (state.history.length > 80) state.history.shift();
      state.future = [];
    }
    state.lastHistoryKey = key;
    state.lastHistoryAt = now;
  }

  function mutate(key, callback, structural = false) {
    recordHistory(key);
    callback();
    state.dirty = true;
    state.lastHistoryAt = Date.now();
    structural ? renderAll() : refreshDerived();
    scheduleSave();
  }

  function restore(value) {
    state.data = copy(value.data);
    state.font = copy(value.font);
    state.selectedId = state.data.pages[value.selectedId] ? value.selectedId : Object.keys(state.data.pages)[0];
    state.dirty = true;
    state.lastHistoryKey = "";
    renderAll();
    scheduleSave();
  }

  function undo() {
    if (!state.history.length) return;
    state.future.push(snapshot());
    restore(state.history.pop());
  }

  function redo() {
    if (!state.future.length) return;
    state.history.push(snapshot());
    restore(state.future.pop());
  }

  function resetHistory() {
    state.history = [];
    state.future = [];
    state.lastHistoryKey = "";
    renderHistory();
  }

  function setSaveState(kind, label) {
    nodes.saveState.dataset.state = kind;
    nodes.saveStateLabel.textContent = label;
  }

  function serializePages() {
    const body = window.jsyaml.dump(state.data, {
      noRefs: true, lineWidth: 120, quoteStyle: "double", forceQuotes: true, sortKeys: false
    });
    return `${state.yamlHeader}${body}`.replace(/[\ue000-\uf8ff]/g, (char) =>
      `\\u${char.codePointAt(0).toString(16).toUpperCase().padStart(4, "0")}`);
  }

  function serializeFont() {
    return `${JSON.stringify(state.font, null, 2)}\n`.replace(/[\ue000-\uf8ff]/g, (char) =>
      `\\u${char.codePointAt(0).toString(16).toUpperCase().padStart(4, "0")}`);
  }

  function scheduleSave() {
    clearTimeout(state.saveTimer);
    if (state.issues.length) return setSaveState("invalid", "Fix validation errors");
    setSaveState("dirty", "Unsaved changes");
    state.saveTimer = setTimeout(() => saveFiles(false), 850);
  }

  async function saveFiles(manual) {
    clearTimeout(state.saveTimer);
    state.issues = validate();
    renderValidation();
    if (state.issues.length) {
      setSaveState("invalid", "Fix validation errors");
      if (manual) toast("Resolve validation errors before saving", true);
      return false;
    }
    state.saving = true;
    nodes.saveButton.disabled = true;
    setSaveState("saving", "Saving");
    try {
      await Promise.all([
        requestText("/api/pages", { method: "PUT", headers: { "Content-Type": "application/yaml" }, body: serializePages() }),
        requestText("/api/font", { method: "PUT", headers: { "Content-Type": "application/json" }, body: serializeFont() })
      ]);
      state.dirty = false;
      setSaveState("saved", "Saved");
      if (state.view === "source") nodes.yamlSource.value = serializePages();
      if (manual) toast("Wiki files saved");
      return true;
    } catch (error) {
      setSaveState("error", "Save failed");
      toast(error.message, true);
      return false;
    } finally {
      state.saving = false;
      nodes.saveButton.disabled = false;
    }
  }

  function issue(message, pageId = null, field = null) { return { message, pageId, field }; }

  function validate() {
    const result = [];
    const wiki = state.data?.wiki;
    const pages = state.data?.pages;
    if (!wiki || typeof wiki !== "object" || Array.isArray(wiki)) return [issue("wiki must be a configuration section")];
    if (typeof wiki.title !== "string" || !wiki.title.trim()) result.push(issue("wiki.title must not be blank"));
    if (wiki.introduction !== undefined && typeof wiki.introduction !== "string") result.push(issue("wiki.introduction must be text"));
    if (wiki.background !== undefined && wiki.background !== false && typeof wiki.background !== "string") {
      result.push(issue("wiki.background must be a MiniMessage string or false"));
    }
    if (!Number.isInteger(wiki.columns) || wiki.columns < 1 || wiki.columns > 4) result.push(issue("wiki.columns must be between 1 and 4"));
    if (!pages || typeof pages !== "object" || Array.isArray(pages) || !Object.keys(pages).length) {
      result.push(issue("Define at least one wiki page"));
      return result;
    }
    const ids = new Set(Object.keys(pages));
    for (const [id, value] of Object.entries(pages)) {
      if (!PAGE_ID.test(id)) result.push(issue(`Invalid page ID: ${id}`, id, "id"));
      if (id === "reload") result.push(issue("The page ID reload is reserved", id, "id"));
      if (!value || typeof value !== "object" || Array.isArray(value)) {
        result.push(issue("Page must be a configuration section", id));
        continue;
      }
      if (typeof value.title !== "string" || !value.title.trim()) result.push(issue("Page title must not be blank", id, "title"));
      if (value.summary !== undefined && value.summary !== null && typeof value.summary !== "string") result.push(issue("Page summary must be text", id));
      if (value.parent !== undefined && (typeof value.parent !== "string" || !ids.has(value.parent))) {
        result.push(issue(`Unknown parent: ${String(value.parent)}`, id, "parent"));
      }
      if (value.parent === id) result.push(issue("A page cannot be its own parent", id, "parent"));
      if (value.background !== undefined && typeof value.background !== "boolean") result.push(issue("Page background must be true or false", id));
      if (value.content !== undefined && value.content !== null && !Array.isArray(value.content)) result.push(issue("Page content must be a list", id));
      if (Array.isArray(value.content) && value.content.some((line) => typeof line !== "string")) result.push(issue("Every content line must be text", id));
      if (value.images !== undefined && !Array.isArray(value.images)) result.push(issue("Page images must be a list", id));
      for (const [index, image] of (Array.isArray(value.images) ? value.images : []).entries()) {
        const single = typeof image?.glyph === "string" && image.glyph.length > 0;
        const grid = Array.isArray(image?.glyphs) && image.glyphs.length > 0 && image.glyphs.every((row) => typeof row === "string");
        if (single === grid) result.push(issue(`Image ${index + 1} needs either glyph or glyphs`, id, "images"));
        if (!Number.isInteger(image?.width) || image.width < 1 || image.width > 1024) result.push(issue(`Image ${index + 1} width must be between 1 and 1024`, id));
        if (!Number.isInteger(image?.height) || image.height < 1 || image.height > 1024) result.push(issue(`Image ${index + 1} height must be between 1 and 1024`, id));
        for (const glyph of imageGlyphs(image)) if (!providerForGlyph(glyph)) result.push(issue(`Undefined glyph ${glyphLabel(glyph)}`, id, "images"));
      }
      for (const text of [value.title, value.summary, ...(Array.isArray(value.content) ? value.content : [])]) {
        if (typeof text !== "string") continue;
        for (const match of text.matchAll(/<page:([a-z0-9][a-z0-9_-]{0,63})(?:\s*\/|>)/gi)) {
          if (!ids.has(match[1])) result.push(issue(`Link points to unknown page: ${match[1]}`, id));
        }
      }
    }
    for (const id of ids) {
      const seen = new Set();
      let current = id;
      while (current && pages[current]?.parent) {
        if (!seen.add(current)) { result.push(issue("Page hierarchy contains a cycle", id, "parent")); break; }
        current = pages[current].parent;
      }
    }
    return result;
  }

  function plainMini(value) {
    if (typeof value !== "string") return "";
    return value.replace(/<page:([a-z0-9_-]+)\s*\/>/gi, (_, id) => plainMini(state.data?.pages?.[id]?.title) || id)
      .replace(/<[^>]+>/g, "").replace(/\\u([0-9a-f]{4})/gi, (_, code) => String.fromCharCode(parseInt(code, 16)));
  }

  function renderMini(container, value) {
    container.replaceChildren();
    const text = typeof value === "string" ? value.replace(/\\u([0-9a-f]{4})/gi, (_, code) => String.fromCharCode(parseInt(code, 16))) : "";
    const stack = [container];
    const tokens = /<([^<>]+)>/g;
    let cursor = 0;
    let match;
    while ((match = tokens.exec(text))) {
      if (match.index > cursor) stack.at(-1).append(document.createTextNode(text.slice(cursor, match.index)));
      const raw = match[1].trim();
      const lower = raw.toLowerCase();
      if (["newline", "newline/", "br", "br/"].includes(lower)) stack.at(-1).append(document.createElement("br"));
      else if (["reset", "reset/"].includes(lower)) stack.length = 1;
      else if (raw.startsWith("/")) { if (stack.length > 1) stack.pop(); }
      else {
        const selfLink = raw.match(/^page:([a-z0-9][a-z0-9_-]{0,63})\s*\/$/i);
        if (selfLink) stack.at(-1).append(el("span", "mm-link", plainMini(state.data?.pages?.[selfLink[1]]?.title) || selfLink[1]));
        else if (!lower.startsWith("font:")) {
          const name = lower.replace(/\/$/, "").split(":", 1)[0];
          const child = el("span", name === "page" ? "mm-link" : "");
          if (COLORS[name]) child.style.color = COLORS[name];
          else if (/^#[0-9a-f]{6}$/i.test(name)) child.style.color = name;
          else if (["bold", "b"].includes(name)) child.style.fontWeight = "700";
          else if (["italic", "i", "em"].includes(name)) child.style.fontStyle = "italic";
          else if (["underlined", "u"].includes(name)) child.style.textDecoration = "underline";
          else if (["strikethrough", "st"].includes(name)) child.style.textDecoration = "line-through";
          stack.at(-1).append(child);
          if (!raw.endsWith("/")) stack.push(child);
        }
      }
      cursor = tokens.lastIndex;
    }
    if (cursor < text.length) stack.at(-1).append(document.createTextNode(text.slice(cursor)));
  }

  function renderToolbar(toolbar) {
    toolbar.replaceChildren();
    const target = toolbar.dataset.target;
    for (const [name, label, open, close] of [
      ["bold", "Bold", "<bold>", "</bold>"], ["italic", "Italic", "<italic>", "</italic>"],
      ["underline", "Underline", "<underlined>", "</underlined>"],
      ["strikethrough", "Strikethrough", "<strikethrough>", "</strikethrough>"]
    ]) {
      const button = iconButton(name, label, "format-button");
      button.addEventListener("click", () => wrapSelection(target, open, close));
      toolbar.append(button);
    }
    toolbar.append(el("span", "format-separator"));
    for (const name of ["gold", "yellow", "green", "aqua", "red", "white", "gray"]) {
      const button = el("button", "color-swatch");
      button.type = "button";
      button.title = `${name} text`;
      button.style.setProperty("--swatch", COLORS[name]);
      button.addEventListener("click", () => wrapSelection(target, `<${name}>`, `</${name}>`));
      toolbar.append(button);
    }
    toolbar.append(el("span", "format-separator"));
    const link = iconButton("link", "Link to wiki page", "format-button");
    link.addEventListener("click", () => insertPageLink(target));
    toolbar.append(link);
  }

  function targetInput(id) { return $(id); }
  function triggerInput(input) { input.dispatchEvent(new Event("input", { bubbles: true })); input.focus(); }
  function wrapSelection(id, open, close) {
    const input = targetInput(id);
    const start = input.selectionStart;
    const selected = input.value.slice(start, input.selectionEnd);
    input.setRangeText(`${open}${selected}${close}`, start, input.selectionEnd, "end");
    input.setSelectionRange(start + open.length, start + open.length + selected.length);
    triggerInput(input);
  }

  function insertPageLink(id) {
    const ids = Object.keys(state.data.pages);
    const suggested = ids.find((value) => value !== state.selectedId) || ids[0];
    const pageId = window.prompt(`Page ID (${ids.slice(0, 10).join(", ")}${ids.length > 10 ? ", ..." : ""})`, suggested);
    if (!pageId) return;
    if (!state.data.pages[pageId]) return toast(`Unknown page: ${pageId}`, true);
    const input = targetInput(id);
    const selected = input.value.slice(input.selectionStart, input.selectionEnd);
    input.setRangeText(selected ? `<page:${pageId}>${selected}</page>` : `<page:${pageId}/>`, input.selectionStart, input.selectionEnd, "end");
    triggerInput(input);
  }

  function allProviders() { return Array.isArray(state.font?.providers) ? state.font.providers : []; }
  function imageGlyphs(image) {
    const text = typeof image?.glyph === "string" ? image.glyph : Array.isArray(image?.glyphs) ? image.glyphs.join("") : "";
    return Array.from(text.replace(/<[^>]+>/g, "")).filter((char) => char.codePointAt(0) >= 0xe000 && char.codePointAt(0) <= 0xf8ff);
  }
  function imageRows(image) { return Array.isArray(image?.glyphs) ? image.glyphs.map((row) => imageGlyphs({ glyph: row })) : [imageGlyphs(image)]; }
  function providerGlyph(provider) { return Array.from((provider.chars || []).join(""))[0] || ""; }
  function providerForGlyph(glyph) { return allProviders().find((provider) => (provider.chars || []).some((row) => Array.from(row).includes(glyph))) || null; }
  function providerFile(provider) {
    const value = String(provider?.file || "");
    return (value.includes(":") ? value.split(":", 2)[1] : value).replace(/^font\//, "");
  }
  function providerName(provider) { return providerFile(provider).replace(/\.png$/i, ""); }
  function glyphLabel(glyph) { return `U+${glyph.codePointAt(0).toString(16).toUpperCase().padStart(4, "0")}`; }
  function glyphUsage(glyph) {
    return entries().reduce((count, [, value]) => count + (value.images || []).filter((image) => imageGlyphs(image).includes(glyph)).length, 0);
  }
  function nextGlyphs(count) {
    const used = new Set(allProviders().flatMap((provider) => (provider.chars || []).flatMap((row) => Array.from(row).map((char) => char.codePointAt(0)))));
    const result = [];
    for (let code = FIRST_GLYPH; code <= 0xf8ff && result.length < count; code += 1) if (!used.has(code)) result.push(String.fromCodePoint(code));
    if (result.length !== count) throw new Error("No free private-use glyphs remain in wiki.json");
    return result;
  }

  function refreshDerived() {
    state.issues = validate();
    renderValidation();
    renderTree();
    renderPreview();
    renderIssues();
    renderHistory();
  }

  function renderAll() {
    refreshDerived();
    document.querySelectorAll(".catalog-tab").forEach((button) => button.classList.toggle("is-active", button.dataset.view === state.view));
    for (const view of ["page", "assets", "settings", "source"]) $(`${view}-view`).hidden = state.view !== view;
    if (state.view === "page") renderPageEditor();
    if (state.view === "assets") renderAssets();
    if (state.view === "settings") renderSettings();
    if (state.view === "source") { nodes.yamlSource.value = serializePages(); nodes.sourceError.hidden = true; }
    refreshIcons();
  }

  function renderHistory() { nodes.undoButton.disabled = !state.history.length; nodes.redoButton.disabled = !state.future.length; }
  function renderValidation() {
    const count = state.issues.length;
    nodes.validationSummary.dataset.level = count ? "error" : "ok";
    nodes.validationLabel.textContent = count ? `${count} validation ${count === 1 ? "error" : "errors"}` : "Catalog valid";
  }

  function treeOrder() {
    const children = new Map();
    for (const [id, value] of entries()) {
      const parent = value.parent && state.data.pages[value.parent] ? value.parent : null;
      if (!children.has(parent)) children.set(parent, []);
      children.get(parent).push(id);
    }
    const result = [];
    const seen = new Set();
    function add(id, depth) {
      if (seen.has(id)) return;
      seen.add(id); result.push({ id, depth });
      for (const child of children.get(id) || []) add(child, depth + 1);
    }
    for (const id of children.get(null) || []) add(id, 0);
    for (const [id] of entries()) add(id, 0);
    return result;
  }

  function renderTree() {
    nodes.pageTree.replaceChildren();
    nodes.pageCount.textContent = Object.keys(state.data.pages).length;
    const query = nodes.pageSearch.value.trim().toLowerCase();
    const broken = new Set(state.issues.map((value) => value.pageId).filter(Boolean));
    const visible = treeOrder().filter(({ id }) => !query || id.includes(query) || plainMini(state.data.pages[id].title).toLowerCase().includes(query));
    if (!visible.length) return nodes.pageTree.append(el("div", "page-tree-empty", "No matching pages"));
    for (const { id, depth } of visible) {
      const value = state.data.pages[id];
      const button = el("button", `page-item${id === state.selectedId ? " is-active" : ""}${depth ? "" : " is-root"}`);
      button.type = "button";
      button.style.setProperty("--depth", depth);
      const text = el("span", "page-item-copy");
      text.append(el("strong", "", plainMini(value.title) || "Untitled"), el("small", "", id));
      button.append(text);
      if (broken.has(id)) button.append(el("span", "issue-marker"));
      button.addEventListener("click", () => selectPage(id));
      nodes.pageTree.append(button);
    }
  }

  function selectPage(id) { state.selectedId = id; state.view = "page"; renderAll(); }
  function isDescendant(candidate, ancestor) {
    const seen = new Set();
    let current = state.data.pages[candidate]?.parent;
    while (current && !seen.has(current)) { if (current === ancestor) return true; seen.add(current); current = state.data.pages[current]?.parent; }
    return false;
  }
  function fillParents(select, current, selected) {
    select.replaceChildren(new Option("No parent (table of contents)", ""));
    for (const [id, value] of entries()) {
      if (id !== current && !isDescendant(id, current)) select.append(new Option(`${plainMini(value.title) || "Untitled"} (${id})`, id));
    }
    select.value = selected || "";
  }

  function renderPageEditor() {
    const value = page();
    if (!value) return;
    nodes.selectedPageTitle.textContent = plainMini(value.title) || "Untitled page";
    nodes.selectedPageId.textContent = state.selectedId;
    nodes.pageParentPath.textContent = value.parent ? `Child of ${value.parent}` : "Root page";
    nodes.pageIdInput.value = state.selectedId;
    nodes.pageIdError.textContent = "";
    fillParents(nodes.pageParent, state.selectedId, value.parent);
    nodes.pageBackground.checked = value.background !== false;
    nodes.pageTitle.value = value.title ?? "";
    nodes.pageSummary.value = value.summary ?? "";
    renderMini(nodes.pageTitlePreview, value.title);
    renderMini(nodes.pageSummaryPreview, value.summary);
    renderContent();
    renderPageImages();
    const index = Object.keys(state.data.pages).indexOf(state.selectedId);
    nodes.moveUp.disabled = index <= 0;
    nodes.moveDown.disabled = index === Object.keys(state.data.pages).length - 1;
  }

  function renderContent() {
    nodes.contentList.replaceChildren();
    const lines = Array.isArray(page().content) ? page().content : [];
    if (!lines.length) return nodes.contentList.append(el("div", "empty-state", "This page has no body text."));
    lines.forEach((line, index) => {
      const item = el("div", "content-item");
      const handle = el("span", "drag-handle");
      handle.draggable = true;
      handle.title = "Drag to reorder content line";
      handle.setAttribute("aria-label", "Drag to reorder content line");
      handle.append(icon("grip-vertical"));
      const main = el("div", "content-item-main");
      const toolbar = el("div", "mini-toolbar");
      const input = document.createElement("textarea");
      input.id = `content-line-${index}`; input.value = line; input.spellcheck = false;
      toolbar.dataset.target = input.id; renderToolbar(toolbar);
      const preview = el("div", "content-preview"); renderMini(preview, line);
      input.addEventListener("input", () => mutate(`content-${state.selectedId}-${index}`, () => { page().content[index] = input.value; renderMini(preview, input.value); }));
      main.append(toolbar, input, preview);
      const remove = iconButton("trash-2", "Remove content line", "icon-button danger-quiet content-remove");
      remove.addEventListener("click", () => mutate("remove-content", () => page().content.splice(index, 1), true));
      item.append(handle, main, remove);
      handle.addEventListener("dragstart", (event) => event.dataTransfer.setData("text/plain", index));
      item.addEventListener("dragover", (event) => event.preventDefault());
      item.addEventListener("drop", (event) => {
        event.preventDefault();
        const from = Number(event.dataTransfer.getData("text/plain"));
        if (!Number.isInteger(from) || from === index) return;
        mutate("reorder-content", () => { const [moved] = page().content.splice(from, 1); page().content.splice(index, 0, moved); }, true);
      });
      nodes.contentList.append(item);
    });
    refreshIcons(nodes.contentList);
  }

  function renderPageImages() {
    nodes.pageImages.replaceChildren();
    const images = page().images || [];
    if (!images.length) return nodes.pageImages.append(el("div", "empty-state", "No image is attached to this page."));
    images.forEach((image, index) => {
      const glyphs = imageGlyphs(image);
      const provider = providerForGlyph(glyphs[0]);
      const item = el("div", "page-image-item");
      const thumb = el("div", "image-thumb");
      if (provider) { const img = document.createElement("img"); img.src = `/api/texture/${encodeURIComponent(providerFile(provider))}`; img.alt = ""; thumb.append(img); }
      const text = el("div", "page-image-copy");
      text.append(el("strong", "", provider ? providerName(provider) : `Image ${index + 1}`),
        el("code", "", glyphs.map(glyphLabel).join(", ") || "No glyph"),
        el("small", "", `${image.width} x ${image.height}; ${imageRows(image).length} row(s)`));
      const remove = iconButton("x", "Detach image", "icon-button danger-quiet");
      remove.addEventListener("click", () => mutate("detach-image", () => page().images.splice(index, 1), true));
      item.append(thumb, text, remove); nodes.pageImages.append(item);
    });
    refreshIcons(nodes.pageImages);
  }

  function renderAssets() {
    const providers = allProviders().filter((value) => value.type === "bitmap");
    let next = "Unavailable";
    try { next = glyphLabel(nextGlyphs(1)[0]); } catch (_) { /* no free glyph */ }
    nodes.assetStats.replaceChildren();
    for (const [value, label] of [[providers.length, "Bitmap providers"], [state.images.textures.length, "Packed PNG files"], [next, "Next image glyph"]]) {
      const stat = el("div", "stat"); stat.append(el("strong", "", String(value)), el("small", "", label)); nodes.assetStats.append(stat);
    }
    nodes.assetList.replaceChildren();
    for (const provider of providers) {
      const glyph = providerGlyph(provider);
      const item = el("div", "asset-item");
      const thumb = el("div", "asset-thumb");
      const img = document.createElement("img"); img.src = `/api/texture/${encodeURIComponent(providerFile(provider))}`; img.alt = ""; thumb.append(img);
      const text = el("div", "asset-copy");
      text.append(el("strong", "", providerName(provider)), el("code", "", `${glyphLabel(glyph)} / ${providerFile(provider)}`),
        el("small", "", `${provider.height}px high, ascent ${provider.ascent}; ${glyphUsage(glyph)} page use(s)`));
      const remove = iconButton("trash-2", "Delete unused asset", "icon-button danger-quiet");
      remove.disabled = glyphUsage(glyph) > 0 || glyph.codePointAt(0) === 0xe000;
      remove.addEventListener("click", () => deleteAsset(provider));
      item.append(thumb, text, remove); nodes.assetList.append(item);
    }
    refreshIcons(nodes.assetList);
  }

  async function deleteAsset(provider) {
    if (!window.confirm(`Delete ${providerFile(provider)} and its font provider?`)) return;
    try {
      await requestText(`/api/texture/${encodeURIComponent(providerFile(provider))}`, { method: "DELETE" });
      state.font.providers = allProviders().filter((value) => value !== provider);
      state.images.textures = state.images.textures.filter((file) => file !== providerFile(provider));
      state.dirty = true; resetHistory(); renderAll(); scheduleSave();
    } catch (error) { toast(error.message, true); }
  }

  function renderSettings() {
    const wiki = state.data.wiki;
    nodes.wikiTitle.value = wiki.title ?? ""; nodes.wikiIntroduction.value = wiki.introduction ?? ""; nodes.wikiColumns.value = wiki.columns ?? 1;
    nodes.wikiBackgroundToggle.checked = typeof wiki.background === "string";
    nodes.wikiBackground.value = typeof wiki.background === "string" ? wiki.background : DEFAULT_BACKGROUND;
    nodes.wikiBackgroundField.hidden = !nodes.wikiBackgroundToggle.checked;
    renderMini(nodes.wikiTitlePreview, wiki.title); renderMini(nodes.wikiIntroductionPreview, wiki.introduction);
  }

  function renderPreview() {
    document.querySelectorAll("[data-preview]").forEach((button) => button.classList.toggle("is-active", button.dataset.preview === state.previewMode));
    nodes.previewTitle.replaceChildren(); nodes.previewBody.replaceChildren(); nodes.previewActions.replaceChildren();
    if (state.previewMode === "contents") {
      renderMini(nodes.previewTitle, state.data.wiki.title);
      const intro = el("div", "preview-line"); renderMini(intro, state.data.wiki.introduction); nodes.previewBody.append(intro);
      nodes.previewActions.style.setProperty("--columns", state.data.wiki.columns || 1);
      for (const [id, value] of entries().filter(([, candidate]) => !candidate.parent)) nodes.previewActions.append(previewButton(id, value));
      return;
    }
    const value = page(); if (!value) return;
    renderMini(nodes.previewTitle, value.title);
    for (const line of value.content || []) { const row = el("div", "preview-line"); renderMini(row, line); nodes.previewBody.append(row); }
    for (const image of value.images || []) nodes.previewBody.append(previewImage(image));
    nodes.previewActions.style.setProperty("--columns", state.data.wiki.columns || 1);
    for (const [id, child] of entries().filter(([, candidate]) => candidate.parent === state.selectedId)) nodes.previewActions.append(previewButton(id, child));
    if (!nodes.previewBody.childNodes.length) nodes.previewBody.append(el("div", "preview-line", "This page has no body content."));
  }

  function previewButton(id, value) {
    const button = el("button", "minecraft-button", plainMini(value.title) || id);
    button.type = "button"; button.title = plainMini(value.summary) || id; button.addEventListener("click", () => selectPage(id)); return button;
  }

  function previewImage(image) {
    const wrapper = el("div", "preview-image"); wrapper.style.width = `${Math.min(image.width || 1, 1024)}px`;
    for (const row of imageRows(image)) {
      const line = el("div", "preview-image-row");
      for (const glyph of row) {
        const provider = providerForGlyph(glyph); if (!provider) continue;
        const img = document.createElement("img"); img.src = `/api/texture/${encodeURIComponent(providerFile(provider))}`;
        img.alt = glyphLabel(glyph); img.style.height = `${Math.min(provider.height || 1, 256)}px`; line.append(img);
      }
      wrapper.append(line);
    }
    return wrapper;
  }

  function renderIssues() {
    nodes.issueList.replaceChildren();
    for (const value of state.issues.slice(0, 20)) {
      const row = el("button", "issue"); row.type = "button";
      row.append(icon("circle-alert"), el("span", "", value.pageId ? `${value.pageId}: ${value.message}` : value.message));
      if (value.pageId && state.data.pages[value.pageId]) row.addEventListener("click", () => selectPage(value.pageId));
      nodes.issueList.append(row);
    }
    refreshIcons(nodes.issueList);
  }

  function renamePage() {
    const oldId = state.selectedId;
    const newId = nodes.pageIdInput.value.trim();
    if (!PAGE_ID.test(newId) || newId === "reload") return nodes.pageIdError.textContent = "Use lowercase letters, numbers, underscores, or hyphens.";
    if (newId !== oldId && state.data.pages[newId]) return nodes.pageIdError.textContent = "That page ID already exists.";
    if (newId === oldId) return;
    mutate("rename-page", () => {
      state.data.pages = Object.fromEntries(entries().map(([id, value]) => [id === oldId ? newId : id, value]));
      for (const value of Object.values(state.data.pages)) {
        if (value.parent === oldId) value.parent = newId;
        for (const key of ["title", "summary"]) if (typeof value[key] === "string") value[key] = replaceLink(value[key], oldId, newId);
        if (Array.isArray(value.content)) value.content = value.content.map((line) => replaceLink(line, oldId, newId));
      }
      state.selectedId = newId;
    }, true);
  }

  function replaceLink(text, oldId, newId) { return text.replace(new RegExp(`<page:${oldId.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}(?=[/>])`, "gi"), `<page:${newId}`); }
  function movePage(offset) {
    const list = entries(); const index = list.findIndex(([id]) => id === state.selectedId); const target = index + offset;
    if (target < 0 || target >= list.length) return;
    mutate("move-page", () => { [list[index], list[target]] = [list[target], list[index]]; state.data.pages = Object.fromEntries(list); }, true);
  }
  function duplicatePage() {
    const base = state.selectedId; let id = `${base}-copy`.slice(0, 64); let number = 2;
    while (state.data.pages[id]) { const suffix = `-${number++}`; id = `${base.slice(0, 64 - suffix.length)}${suffix}`; }
    mutate("duplicate-page", () => {
      const list = entries(); const index = list.findIndex(([value]) => value === base); const duplicate = copy(page());
      duplicate.title = `${duplicate.title || "Untitled"} Copy`; list.splice(index + 1, 0, [id, duplicate]);
      state.data.pages = Object.fromEntries(list); state.selectedId = id;
    }, true);
  }
  function deletePage() {
    if (Object.keys(state.data.pages).length <= 1) return toast("The catalog must contain at least one page", true);
    const id = state.selectedId; if (!window.confirm(`Delete page ${id}? Child pages will move to its parent.`)) return;
    const parent = page().parent;
    mutate("delete-page", () => {
      const next = {};
      for (const [current, value] of entries()) {
        if (current === id) continue;
        if (value.parent === id) { if (parent) value.parent = parent; else delete value.parent; }
        next[current] = value;
      }
      state.data.pages = next; state.selectedId = Object.keys(next)[0];
    }, true);
  }

  function openPageDialog() {
    nodes.newPageId.value = ""; nodes.newPageTitle.value = ""; fillParents(nodes.newPageParent, null, state.selectedId);
    nodes.pageDialog.showModal(); nodes.newPageId.focus();
  }
  function createPage(event) {
    event.preventDefault(); const id = nodes.newPageId.value.trim(); const title = nodes.newPageTitle.value.trim();
    if (!PAGE_ID.test(id) || id === "reload" || state.data.pages[id]) return toast("Enter an unused, valid page ID", true);
    const parent = nodes.newPageParent.value;
    mutate("create-page", () => { state.data.pages[id] = { ...(parent ? { parent } : {}), title, summary: "", content: [] }; state.selectedId = id; state.view = "page"; }, true);
    nodes.pageDialog.close();
  }

  function applySource() {
    try {
      const parsed = window.jsyaml.load(nodes.yamlSource.value);
      if (!parsed?.wiki || !parsed?.pages) throw new Error("YAML must contain wiki and pages sections");
      recordHistory("apply-yaml"); state.data = parsed;
      state.selectedId = parsed.pages[state.selectedId] ? state.selectedId : Object.keys(parsed.pages)[0]; state.dirty = true;
      nodes.sourceError.hidden = true; renderAll(); scheduleSave(); toast("YAML applied to the editor");
    } catch (error) { nodes.sourceError.textContent = error.message; nodes.sourceError.hidden = false; }
  }

  function toast(message, error = false) {
    const node = el("div", `toast${error ? " is-error" : ""}`, message); nodes.toasts.append(node); setTimeout(() => node.remove(), 4200);
  }
  function slug(name) { return name.toLowerCase().replace(/\.png$/i, "").replace(/[^a-z0-9_-]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 96); }

  function openImageDialog() {
    if (!page()) return toast("Select the page that should receive the image", true);
    state.importImage = null; nodes.imageFile.value = ""; nodes.imageName.value = ""; nodes.processImage.disabled = true;
    nodes.imageDropEmpty.hidden = false; nodes.imageWidth.value = "464"; nodes.imageWidthOutput.value = "464 px"; nodes.imageAscent.value = "7";
    nodes.imagePage.replaceChildren(...entries().map(([id, value]) => new Option(`${plainMini(value.title) || "Untitled"} (${id})`, id)));
    nodes.imagePage.value = state.selectedId;
    updateImageImport(); nodes.imageDialog.showModal();
  }

  async function loadImage(file) {
    if (!file || file.type !== "image/png") return toast("Select a PNG image", true);
    const url = URL.createObjectURL(file); const image = new Image();
    try {
      await new Promise((resolve, reject) => { image.onload = resolve; image.onerror = () => reject(new Error("The PNG could not be decoded")); image.src = url; });
      state.importImage = { file, image, url }; nodes.imageName.value = slug(file.name);
      nodes.imageWidth.value = Math.min(960, Math.max(32, image.naturalWidth)); nodes.imageDropEmpty.hidden = true; nodes.processImage.disabled = false;
      updateImageImport();
    } catch (error) { URL.revokeObjectURL(url); toast(error.message, true); }
  }

  function imageLayout() {
    if (!state.importImage) return null;
    const sourceWidth = state.importImage.image.naturalWidth; const sourceHeight = state.importImage.image.naturalHeight;
    const requestedWidth = Math.min(960, Math.max(32, Number(nodes.imageWidth.value) || 464));
    const requestedHeight = Math.max(1, Math.round(requestedWidth * sourceHeight / sourceWidth));
    const columns = Math.ceil(requestedWidth / TILE_SIZE); const rows = Math.ceil(requestedHeight / TILE_SIZE);
    const tileWidth = Math.ceil(requestedWidth / columns); const tileHeight = Math.ceil(requestedHeight / rows);
    const outputWidth = tileWidth * columns; const outputHeight = tileHeight * rows;
    let providerHeight = tileHeight;
    while (providerHeight > 1 && columns * (Math.ceil(tileWidth * providerHeight / tileHeight) + 1) > outputWidth) providerHeight--;
    return {
      sourceWidth, sourceHeight, columns, rows, tileWidth, tileHeight, outputWidth, outputHeight,
      providerHeight, renderedHeight: providerHeight * rows
    };
  }

  function updateImageImport() {
    nodes.imageWidthOutput.value = `${nodes.imageWidth.value} px`;
    const canvas = nodes.imageCanvas; const context = canvas.getContext("2d"); context.clearRect(0, 0, canvas.width, canvas.height);
    context.fillStyle = "#dfe2e5"; context.fillRect(0, 0, canvas.width, canvas.height);
    const layout = imageLayout();
    if (!layout) {
      nodes.metricSource.textContent = "0 x 0"; nodes.metricOutput.textContent = "0 x 0"; nodes.metricTiles.textContent = "0"; nodes.metricGlyph.textContent = "-"; return;
    }
    const scale = Math.min(canvas.width / layout.outputWidth, canvas.height / layout.outputHeight);
    const width = layout.outputWidth * scale; const height = layout.outputHeight * scale;
    const left = (canvas.width - width) / 2; const top = (canvas.height - height) / 2;
    context.drawImage(state.importImage.image, left, top, width, height); context.strokeStyle = "rgba(184,32,43,.8)";
    for (let column = 1; column < layout.columns; column++) { const x = left + column * layout.tileWidth * scale; context.beginPath(); context.moveTo(x, top); context.lineTo(x, top + height); context.stroke(); }
    for (let row = 1; row < layout.rows; row++) { const y = top + row * layout.tileHeight * scale; context.beginPath(); context.moveTo(left, y); context.lineTo(left + width, y); context.stroke(); }
    nodes.metricSource.textContent = `${layout.sourceWidth} x ${layout.sourceHeight}`; nodes.metricOutput.textContent = `${layout.outputWidth} x ${layout.renderedHeight}`;
    nodes.metricTiles.textContent = layout.columns * layout.rows; nodes.metricGlyph.textContent = glyphLabel(nextGlyphs(layout.columns * layout.rows)[0]);
  }

  function pngBlob(canvas) { return new Promise((resolve, reject) => canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error("PNG encoding failed")), "image/png")); }
  async function processImage(event) {
    event.preventDefault(); if (!state.importImage) return;
    const targetPage = state.data.pages[nodes.imagePage.value]; if (!targetPage) return toast("Select a page for this image", true);
    const name = nodes.imageName.value.trim(); if (!IMAGE_NAME.test(name)) return toast("Asset names use lowercase letters, numbers, underscores, or hyphens", true);
    const layout = imageLayout(); if (layout.outputWidth > 1024 || layout.outputHeight > 1024) return toast("The rendered image must fit within 1024 x 1024", true);
    const ascent = Math.round(Number(nodes.imageAscent.value) || 7);
    if (ascent > layout.providerHeight) return toast(`Ascent must not exceed the generated ${layout.providerHeight}px glyph height`, true);
    const glyphs = nextGlyphs(layout.columns * layout.rows); const names = [];
    for (let row = 0; row < layout.rows; row++) for (let column = 0; column < layout.columns; column++) names.push(layout.rows * layout.columns === 1 ? `${name}.png` : `${name}_${row + 1}_${column + 1}.png`);
    const collision = names.find((file) => state.images.textures.includes(file)); if (collision) return toast(`Packed image already exists: ${collision}`, true);
    if (state.images.sourceImages.includes(`${name}.png`)) return toast(`Source image already exists: ${name}.png`, true);
    nodes.processImage.disabled = true;
    const scaled = document.createElement("canvas"); scaled.width = layout.outputWidth; scaled.height = layout.outputHeight;
    const scaledContext = scaled.getContext("2d"); scaledContext.imageSmoothingQuality = "high"; scaledContext.drawImage(state.importImage.image, 0, 0, scaled.width, scaled.height);
    try {
      const uploads = []; const providers = []; const rows = []; let index = 0;
      for (let row = 0; row < layout.rows; row++) {
        let rowGlyphs = "";
        for (let column = 0; column < layout.columns; column++) {
          const tile = document.createElement("canvas"); tile.width = layout.tileWidth; tile.height = layout.tileHeight;
          tile.getContext("2d").drawImage(scaled, column * layout.tileWidth, row * layout.tileHeight, layout.tileWidth, layout.tileHeight, 0, 0, layout.tileWidth, layout.tileHeight);
          const blob = await pngBlob(tile); const file = names[index]; const glyph = glyphs[index++];
          uploads.push(requestText(`/api/texture/${encodeURIComponent(file)}`, { method: "PUT", headers: { "Content-Type": "image/png" }, body: blob }));
          providers.push({ type: "bitmap", file: `guidelegowelt:font/${file}`, ascent, height: layout.providerHeight, chars: [glyph] });
          rowGlyphs += glyph;
        }
        rows.push(`<font:guidelegowelt:wiki>${rowGlyphs}</font>`);
      }
      uploads.push(requestText(`/api/source-image/${encodeURIComponent(`${name}.png`)}`, { method: "PUT", headers: { "Content-Type": "image/png" }, body: state.importImage.file }));
      await Promise.all(uploads);
      const updatedImages = await requestJson("/api/images");
      state.font.providers.push(...providers);
      if (!Array.isArray(targetPage.images)) targetPage.images = [];
      targetPage.images.push(rows.length === 1 && layout.columns === 1 ? { glyph: rows[0], width: layout.outputWidth, height: layout.renderedHeight } : { glyphs: rows, width: layout.outputWidth, height: layout.renderedHeight });
      state.images = updatedImages; state.dirty = true; resetHistory(); nodes.imageDialog.close(); renderAll(); await saveFiles(false);
      toast(`Imported ${names.length} packed image ${names.length === 1 ? "tile" : "tiles"}`);
    } catch (error) {
      await Promise.allSettled([
        ...names.map((file) => requestText(`/api/texture/${encodeURIComponent(file)}`, { method: "DELETE" })),
        requestText(`/api/source-image/${encodeURIComponent(`${name}.png`)}`, { method: "DELETE" })
      ]);
      state.images = await requestJson("/api/images");
      toast(error.message, true); nodes.processImage.disabled = false;
    }
  }

  function bindInput(node, key, update, preview) {
    node.addEventListener("input", () => mutate(key, () => { update(node.value); if (preview) renderMini(preview, node.value); }));
  }

  function bindEvents() {
    nodes.saveButton.addEventListener("click", () => saveFiles(true));
    nodes.reloadButton.addEventListener("click", () => (!state.dirty || window.confirm("Discard unsaved editor changes and reload files?")) && loadFiles());
    nodes.stopButton.addEventListener("click", async () => { if (state.dirty && !(await saveFiles(true))) return; await requestText("/api/shutdown", { method: "POST" }); setSaveState("saved", "Editor stopped"); });
    nodes.undoButton.addEventListener("click", undo); nodes.redoButton.addEventListener("click", redo); nodes.pageSearch.addEventListener("input", renderTree);
    nodes.addPage.addEventListener("click", openPageDialog);
    nodes.pageDialogForm.addEventListener("submit", (event) => { if (event.submitter?.value !== "cancel") createPage(event); });
    document.querySelectorAll(".catalog-tab").forEach((button) => button.addEventListener("click", () => { state.view = button.dataset.view; renderAll(); }));
    document.querySelectorAll("[data-preview]").forEach((button) => button.addEventListener("click", () => { state.previewMode = button.dataset.preview; renderPreview(); }));
    nodes.renamePage.addEventListener("click", renamePage); nodes.pageIdInput.addEventListener("keydown", (event) => event.key === "Enter" && renamePage());
    nodes.pageParent.addEventListener("change", () => mutate("page-parent", () => { if (nodes.pageParent.value) page().parent = nodes.pageParent.value; else delete page().parent; }, true));
    nodes.pageBackground.addEventListener("change", () => mutate("page-background", () => { if (nodes.pageBackground.checked) delete page().background; else page().background = false; }));
    bindInput(nodes.pageTitle, "page-title", (value) => page().title = value, nodes.pageTitlePreview);
    bindInput(nodes.pageSummary, "page-summary", (value) => page().summary = value, nodes.pageSummaryPreview);
    nodes.addContent.addEventListener("click", () => mutate("add-content", () => { if (!Array.isArray(page().content)) page().content = []; page().content.push(""); }, true));
    nodes.importImage.addEventListener("click", openImageDialog); nodes.assetsImport.addEventListener("click", openImageDialog);
    nodes.moveUp.addEventListener("click", () => movePage(-1)); nodes.moveDown.addEventListener("click", () => movePage(1));
    nodes.duplicatePage.addEventListener("click", duplicatePage); nodes.deletePage.addEventListener("click", deletePage);
    bindInput(nodes.wikiTitle, "wiki-title", (value) => state.data.wiki.title = value, nodes.wikiTitlePreview);
    bindInput(nodes.wikiIntroduction, "wiki-introduction", (value) => state.data.wiki.introduction = value, nodes.wikiIntroductionPreview);
    nodes.wikiColumns.addEventListener("input", () => mutate("wiki-columns", () => state.data.wiki.columns = Number(nodes.wikiColumns.value)));
    nodes.wikiBackgroundToggle.addEventListener("change", () => mutate("wiki-background-toggle", () => { state.data.wiki.background = nodes.wikiBackgroundToggle.checked ? nodes.wikiBackground.value || DEFAULT_BACKGROUND : false; nodes.wikiBackgroundField.hidden = !nodes.wikiBackgroundToggle.checked; }));
    nodes.wikiBackground.addEventListener("input", () => mutate("wiki-background", () => state.data.wiki.background = nodes.wikiBackground.value));
    nodes.applySource.addEventListener("click", applySource); nodes.imageFile.addEventListener("change", () => loadImage(nodes.imageFile.files[0]));
    nodes.imageDrop.addEventListener("dragover", (event) => event.preventDefault()); nodes.imageDrop.addEventListener("drop", (event) => { event.preventDefault(); loadImage(event.dataTransfer.files[0]); });
    nodes.imageWidth.addEventListener("input", updateImageImport); nodes.imageDialogForm.addEventListener("submit", (event) => { if (event.submitter?.value !== "cancel") processImage(event); });
    window.addEventListener("keydown", (event) => {
      if (!(event.ctrlKey || event.metaKey)) return;
      if (event.key.toLowerCase() === "s") { event.preventDefault(); saveFiles(true); }
      if (event.key.toLowerCase() === "z" && !event.shiftKey) { event.preventDefault(); undo(); }
      if (event.key.toLowerCase() === "y" || (event.key.toLowerCase() === "z" && event.shiftKey)) { event.preventDefault(); redo(); }
    });
    window.addEventListener("beforeunload", (event) => { if (state.dirty) { event.preventDefault(); event.returnValue = ""; } });
  }

  function collectNodes() {
    const ids = {
      workspace: "workspace", saveState: "save-state", saveStateLabel: "save-state-label", saveButton: "save-button", reloadButton: "reload-button",
      stopButton: "stop-button", undoButton: "undo-button", redoButton: "redo-button", pageCount: "page-count", pageSearch: "page-search",
      pageTree: "page-tree", validationSummary: "validation-summary", validationLabel: "validation-label", addPage: "add-page-button",
      selectedPageTitle: "selected-page-title", selectedPageId: "selected-page-id", pageParentPath: "page-parent-path", pageIdInput: "page-id-input",
      pageIdError: "page-id-error", renamePage: "rename-page-button", pageParent: "page-parent-select", pageBackground: "page-background-toggle",
      pageTitle: "page-title-input", pageTitlePreview: "page-title-inline-preview", pageSummary: "page-summary-input",
      pageSummaryPreview: "page-summary-inline-preview", contentList: "content-list", addContent: "add-content-button", pageImages: "page-images",
      importImage: "import-image-button", moveUp: "move-up-button", moveDown: "move-down-button", duplicatePage: "duplicate-page-button",
      deletePage: "delete-page-button", assetStats: "asset-stats", assetList: "asset-list", assetsImport: "assets-import-button",
      wikiTitle: "wiki-title-input", wikiTitlePreview: "wiki-title-inline-preview", wikiIntroduction: "wiki-introduction-input",
      wikiIntroductionPreview: "wiki-introduction-inline-preview", wikiColumns: "wiki-columns-input", wikiBackgroundToggle: "wiki-background-toggle",
      wikiBackground: "wiki-background-input", wikiBackgroundField: "wiki-background-field", yamlSource: "yaml-source-input", applySource: "apply-source-button",
      sourceError: "source-error", previewTitle: "preview-title", previewBody: "preview-body", previewActions: "preview-actions", issueList: "issue-list",
      pageDialog: "page-dialog", pageDialogForm: "page-dialog-form", newPageId: "new-page-id", newPageTitle: "new-page-title",
      newPageParent: "new-page-parent", imageDialog: "image-dialog", imageDialogForm: "image-dialog-form", imageDrop: "image-drop",
      imageDropEmpty: "image-drop-empty", imageFile: "image-file-input", imageCanvas: "image-import-preview", imageName: "image-name-input",
      imagePage: "image-page-select",
      imageWidth: "image-width-input", imageWidthOutput: "image-width-output", imageAscent: "image-ascent-input", metricSource: "metric-source",
      metricOutput: "metric-output", metricTiles: "metric-tiles", metricGlyph: "metric-glyph", processImage: "process-image-button", toasts: "toast-region"
    };
    for (const [name, id] of Object.entries(ids)) nodes[name] = $(id);
  }

  async function loadFiles() {
    setSaveState("loading", "Loading"); nodes.workspace.setAttribute("aria-busy", "true");
    try {
      const [yaml, font, images] = await Promise.all([requestText("/api/pages", { cache: "no-store" }), requestText("/api/font", { cache: "no-store" }), requestJson("/api/images")]);
      const header = yaml.match(/^[\s\S]*?(?=^wiki:\s*$)/m); state.yamlHeader = header ? header[0] : "";
      state.data = window.jsyaml.load(yaml); state.font = JSON.parse(font); state.images = images;
      if (!state.data?.wiki || !state.data?.pages || !Array.isArray(state.font?.providers)) throw new Error("Wiki source files have an unsupported structure");
      state.selectedId = Object.keys(state.data.pages)[0]; state.history = []; state.future = []; state.dirty = false; state.lastHistoryKey = "";
      $("loading-state").hidden = true; nodes.workspace.setAttribute("aria-busy", "false"); renderAll(); setSaveState("saved", "Saved");
    } catch (error) { setSaveState("error", "Load failed"); toast(error.message, true); }
  }

  function initialize() {
    collectNodes(); document.querySelectorAll(".mini-toolbar[data-target]").forEach(renderToolbar); bindEvents(); refreshIcons(); loadFiles();
  }
  document.addEventListener("DOMContentLoaded", initialize);
}());
