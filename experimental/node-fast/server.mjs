// screenpilot-fast — Windows 桌面自动化 MCP 服务（GDI + SendInput，无 OCR）
// stdio / NDJSON / JSON-RPC 2.0
import koffi from 'koffi';
import zlib from 'node:zlib';
import readline from 'node:readline';

const user32 = koffi.load('user32.dll');
const gdi32 = koffi.load('gdi32.dll');

const SetProcessDPIAware = user32.func('int SetProcessDPIAware()');
const GetSystemMetrics = user32.func('int GetSystemMetrics(int)');
const GetDC = user32.func('void *GetDC(void *)');
const ReleaseDC = user32.func('int ReleaseDC(void *, void *)');
const SetCursorPos = user32.func('int SetCursorPos(int, int)');
const mouse_event = user32.func('void mouse_event(uint32, int32, int32, uint32, void *)');
const keybd_event = user32.func('void keybd_event(uint8, uint8, uint32, void *)');
const SendInput = user32.func('uint32 SendInput(uint32, void *, int)');
const EnumWindows = user32.func('int EnumWindows(void *, void *)');
const GetWindowTextW = user32.func('int GetWindowTextW(void *, void *, int)');
const IsWindowVisible = user32.func('int IsWindowVisible(void *)');
const IsIconic = user32.func('int IsIconic(void *)');
const ShowWindow = user32.func('int ShowWindow(void *, int)');
const SetForegroundWindow = user32.func('int SetForegroundWindow(void *)');
const PostMessageW = user32.func('int PostMessageW(void *, uint32, void *, void *)');
const GetWindowThreadProcessId = user32.func('uint32 GetWindowThreadProcessId(void *, void *)');
const GetWindowRect = user32.func('int GetWindowRect(void *, void *)');

const CreateCompatibleDC = gdi32.func('void *CreateCompatibleDC(void *)');
const CreateCompatibleBitmap = gdi32.func('void *CreateCompatibleBitmap(void *, int, int)');
const SelectObject = gdi32.func('void *SelectObject(void *, void *)');
const BitBlt = gdi32.func('int BitBlt(void *, int, int, int, int, void *, int, int, uint32)');
const GetDIBits = gdi32.func('int GetDIBits(void *, void *, uint32, uint32, void *, void *, uint32)');
const DeleteObject = gdi32.func('int DeleteObject(void *)');
const DeleteDC = gdi32.func('int DeleteDC(void *)');

SetProcessDPIAware();
const SCREEN_W = GetSystemMetrics(0);
const SCREEN_H = GetSystemMetrics(1);

// ---------- 截屏 ----------
function grab(region) {
  const x = region?.x ?? 0, y = region?.y ?? 0;
  const w = Math.min(region?.w ?? SCREEN_W, SCREEN_W - x);
  const h = Math.min(region?.h ?? SCREEN_H, SCREEN_H - y);
  if (w <= 0 || h <= 0) throw new Error('region out of screen');

  const hdc = GetDC(null);
  const mem = CreateCompatibleDC(hdc);
  const bmp = CreateCompatibleBitmap(hdc, w, h);
  const old = SelectObject(mem, bmp);
  BitBlt(mem, 0, 0, w, h, hdc, x, y, 0x00cc0020 | 0x40000000);
  const bmi = Buffer.alloc(64);
  bmi.writeUInt32LE(40, 0);
  bmi.writeInt32LE(w, 4);
  bmi.writeInt32LE(-h, 8);
  bmi.writeUInt16LE(1, 12);
  bmi.writeUInt16LE(32, 14);
  const bits = Buffer.alloc(w * h * 4);
  GetDIBits(mem, bmp, 0, h, bits, bmi, 0);
  SelectObject(mem, old);
  DeleteObject(bmp);
  DeleteDC(mem);
  ReleaseDC(null, hdc);
  return { bits, w, h, x, y };
}

// BGRA(底往上已翻转) → 降采样后的 RGBA 行缓冲（PNG filter 0）
function toRows(frame, scale) {
  const { bits, w, h } = frame;
  const ow = Math.max(1, Math.round(w * scale));
  const oh = Math.max(1, Math.round(h * scale));
  const rows = Buffer.alloc((ow * 4 + 1) * oh);
  const stepX = w / ow, stepY = h / oh;
  for (let y = 0; y < oh; y++) {
    const sy = Math.min(h - 1, (y * stepY) | 0);
    const base = y * (ow * 4 + 1);
    rows[base] = 0;
    for (let x = 0; x < ow; x++) {
      const sx = Math.min(w - 1, (x * stepX) | 0);
      const si = (sy * w + sx) * 4;
      const di = base + 1 + x * 4;
      rows[di] = bits[si + 2]; rows[di + 1] = bits[si + 1]; rows[di + 2] = bits[si]; rows[di + 3] = 255;
    }
  }
  return { rows, w: ow, h: oh };
}

const crcTable = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c; }
  return t;
})();
const crc32 = (b) => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = crcTable[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
};
function encodePng({ rows, w, h }) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(rows, { level: 1 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---------- 增量：32px tile 采样 hash ----------
const TILE = 32;
function tilesOf({ rows, w, h }) {
  const cols = Math.ceil(w / TILE), rowsN = Math.ceil(h / TILE);
  const hashes = new Uint32Array(cols * rowsN);
  const stride = w * 4 + 1;
  for (let ty = 0; ty < rowsN; ty++) {
    for (let tx = 0; tx < cols; tx++) {
      let hsh = 2166136261;
      for (let y = ty * TILE; y < Math.min(h - 1, (ty + 1) * TILE); y += 4) {
        for (let x = tx * TILE; x < Math.min(w, (tx + 1) * TILE); x += 4) {
          const i = y * stride + 1 + x * 4;
          hsh = ((hsh ^ rows[i]) * 16777619) >>> 0;
          hsh = ((hsh ^ rows[i + 1]) * 16777619) >>> 0;
          hsh = ((hsh ^ rows[i + 2]) * 16777619) >>> 0;
        }
      }
      hashes[ty * cols + tx] = hsh >>> 0;
    }
  }
  return { cols, rows: rowsN, hashes };
}

let last = null; // {key, cols, rows, hashes, w, h, x, y, scale}

// ---------- 输入 ----------
const VK = {
  ctrl: 0x11, control: 0x11, shift: 0x10, alt: 0x12, win: 0x5b, meta: 0x5b,
  enter: 0x0d, return: 0x0d, tab: 0x09, esc: 0x1b, escape: 0x1b, space: 0x20,
  backspace: 0x08, delete: 0x2e, del: 0x2e, insert: 0x2d, home: 0x24, end: 0x23,
  pageup: 0x21, pagedown: 0x22, up: 0x26, down: 0x28, left: 0x25, right: 0x27,
  capslock: 0x14, '-': 0xbd, '=': 0xbb, '[': 0xdb, ']': 0xdd, '\\': 0xdc,
  ';': 0xba, "'": 0xde, ',': 0xbc, '.': 0xbe, '/': 0xbf, '`': 0xc0,
};
for (let i = 0; i < 26; i++) VK[String.fromCharCode(65 + i).toLowerCase()] = 0x41 + i;
for (let i = 0; i < 10; i++) VK[String(i)] = 0x30 + i;
for (let i = 1; i <= 12; i++) VK['f' + i] = 0x6f + i;

function keyDown(vk) { keybd_event(vk, 0, 0, null); }
function keyUp(vk) { keybd_event(vk, 0, 2, null); }

function pressCombo(combo) {
  const parts = String(combo).toLowerCase().split('+').map((s) => s.trim()).filter(Boolean);
  const vks = parts.map((p) => {
    const vk = VK[p];
    if (vk === undefined) throw new Error('unknown key: ' + p);
    return vk;
  });
  for (const vk of vks) keyDown(vk);
  for (let i = vks.length - 1; i >= 0; i--) keyUp(vks[i]);
  return vks.length;
}

function cursor(x, y) { SetCursorPos(Math.round(x), Math.round(y)); }
function mouse(flags) { mouse_event(flags, 0, 0, 0, null); }
const MD = { left: [0x0002, 0x0004], right: [0x0008, 0x0010], middle: [0x0020, 0x0040] };

function clickAt(x, y, button = 'left', count = 1) {
  cursor(x, y);
  const [down, up] = MD[button] ?? MD.left;
  for (let i = 0; i < count; i++) { mouse(down); mouse(up); }
}

function typeText(text) {
  const units = [];
  for (const ch of String(text)) {
    const cp = ch.codePointAt(0);
    if (cp > 0xffff) {
      const v = cp - 0x10000;
      units.push(0xd800 + (v >> 10), 0xdc00 + (v & 0x3ff));
    } else units.push(cp);
  }
  const buf = Buffer.alloc(40 * units.length * 2);
  let off = 0;
  const put = (wScan, flags) => {
    buf.writeUInt32LE(1, off);           // INPUT_KEYBOARD
    buf.writeUInt16LE(0, off + 8);       // wVk
    buf.writeUInt16LE(wScan, off + 10);  // wScan
    buf.writeUInt32LE(flags, off + 12);  // dwFlags
    off += 40;
  };
  for (const u of units) { put(u, 0x0004); put(u, 0x0004 | 0x0002); }
  const r = SendInput(units.length * 2, buf, 40);
  if (r !== units.length * 2) throw new Error('SendInput injected ' + r + '/' + (units.length * 2));
}

// ---------- 窗口 ----------
const WNDENUMPROC = koffi.register((hwnd, lparam) => {
  const buf = Buffer.alloc(512 * 2);
  const len = GetWindowTextW(hwnd, buf, 512);
  const title = len ? buf.toString('utf16le', 0, len * 2) : '';
  if (title && IsWindowVisible(hwnd)) wndList.push({ hwnd, title, minimized: !!IsIconic(hwnd) });
  return true;
}, koffi.pointer(koffi.proto('bool', ['void *', 'void *'])));
let wndList = [];

function listWindows() {
  wndList = [];
  EnumWindows(WNDENUMPROC, null);
  return wndList.map((w) => {
    const r = Buffer.alloc(16);
    GetWindowRect(w.hwnd, r);
    return { title: w.title, minimized: w.minimized, rect: { x: r.readInt32LE(0), y: r.readInt32LE(4), w: r.readInt32LE(8) - r.readInt32LE(0), h: r.readInt32LE(12) - r.readInt32LE(4) } };
  });
}

function findWindow(title, hwndHex) {
  if (hwndHex) return { hwnd: koffi.as(BigInt(hwndHex), 'void *') };
  wndList = [];
  EnumWindows(WNDENUMPROC, null);
  const q = String(title).toLowerCase();
  const hit = wndList.find((w) => w.title.toLowerCase().includes(q));
  if (!hit) throw new Error('window not found: ' + title);
  return hit;
}

// ---------- 工具实现 ----------
async function doCapture(args = {}) {
  const t0 = performance.now();
  const region = args.region ? { x: args.region.x | 0, y: args.region.y | 0, w: args.region.w | 0, h: args.region.h | 0 } : null;
  const maxW = args.max_width ?? 1280;
  const base = region ? Math.min(1, maxW / region.w) : Math.min(1, maxW / SCREEN_W);
  const scale = typeof args.scale === 'number' ? Math.max(0.05, Math.min(1, args.scale)) : base;

  const frame = grab(region);
  const down = toRows(frame, scale);
  const meta = frameToMeta(frame, down, scale, region);
  const tiles = tilesOf(down);

  if (args.diff) {
    const prev = last;
    const sameShape = prev && prev.w === down.w && prev.h === down.h && prev.x === frame.x && prev.y === frame.y;
    if (sameShape) {
      const changed = [];
      let x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1;
      for (let ty = 0; ty < tiles.rows; ty++) {
        for (let tx = 0; tx < tiles.cols; tx++) {
          const i = ty * tiles.cols + tx;
          if (tiles.hashes[i] !== prev.hashes[i]) {
            changed.push([tx, ty]);
            x0 = Math.min(x0, tx); y0 = Math.min(y0, ty);
            x1 = Math.max(x1, tx); y1 = Math.max(y1, ty);
          }
        }
      }
      last = { ...tiles, w: down.w, h: down.h, x: frame.x, y: frame.y };
      if (!changed.length) {
        return { content: [{ type: 'text', text: JSON.stringify({ ...meta, changed_tiles: 0, unchanged: true, ms: Math.round(performance.now() - t0) }) }] };
      }
      const crop = cropRows(down, x0 * TILE, y0 * TILE, Math.min(down.w, (x1 + 1) * TILE) - x0 * TILE, Math.min(down.h, (y1 + 1) * TILE) - y0 * TILE);
      const png = encodePng(crop);
      return {
        content: [
          { type: 'image', data: png.toString('base64'), mimeType: 'image/png' },
          { type: 'text', text: JSON.stringify({ ...meta, changed_tiles: changed.length, changed_rect: { x: x0 * TILE, y: y0 * TILE, w: crop.w, h: crop.h }, image_size: { w: crop.w, h: crop.h }, ms: Math.round(performance.now() - t0) }) },
        ],
      };
    }
  }

  last = { ...tiles, w: down.w, h: down.h, x: frame.x, y: frame.y };
  const png = encodePng(down);
  return {
    content: [
      { type: 'image', data: png.toString('base64'), mimeType: 'image/png' },
      { type: 'text', text: JSON.stringify({ ...meta, bytes: png.length, ms: Math.round(performance.now() - t0) }) },
    ],
  };
}

function frameToMeta(frame, down, scale, region) {
  return {
    image: { w: down.w, h: down.h },
    origin: { x: frame.x, y: frame.y },
    scale: +(frame.w / down.w).toFixed(4),
    screen: { w: SCREEN_W, h: SCREEN_H },
    region: region ? { x: region.x, y: region.y, w: frame.w, h: frame.h } : { x: 0, y: 0, w: frame.w, h: frame.h },
  };
}

function cropRows(down, x, y, w, h) {
  const stride = down.w * 4 + 1;
  const out = Buffer.alloc((w * 4 + 1) * h);
  for (let i = 0; i < h; i++) {
    out[i * (w * 4 + 1)] = 0;
    down.rows.copy(out, i * (w * 4 + 1) + 1, (y + i) * stride + 1 + x * 4, (y + i) * stride + 1 + (x + w) * 4);
  }
  return { rows: out, w, h };
}

async function doWaitStable(args = {}) {
  const timeout = args.timeout_ms ?? 3000;
  const interval = args.interval_ms ?? 40;
  const region = args.region ? { x: args.region.x | 0, y: args.region.y | 0, w: args.region.w | 0, h: args.region.h | 0 } : null;
  const t0 = Date.now();
  const scale = 0.25;
  let prevSig = null, stable = 0;
  while (Date.now() - t0 < timeout) {
    const f = grab(region);
    const d = toRows(f, scale);
    const t = tilesOf(d);
    const sig = Buffer.from(t.hashes.buffer).toString('base64');
    if (sig === prevSig) stable++;
    else stable = 0;
    prevSig = sig;
    if (stable >= 1) return { content: [{ type: 'text', text: JSON.stringify({ stable: true, waited_ms: Date.now() - t0 }) }] };
    await new Promise((r) => setTimeout(r, interval));
  }
  return { content: [{ type: 'text', text: JSON.stringify({ stable: false, waited_ms: Date.now() - t0 }) }] };
}

const TOOLS = [
  {
    name: 'capture',
    description: '截屏，返回 PNG + 坐标元数据。region 限定区域，scale 缩放，max_width 限制宽度（默认 1280），diff=true 时只返回相对上一帧变化的区域。image 像素坐标 × scale + origin = 屏幕物理坐标。',
    inputSchema: {
      type: 'object',
      properties: {
        region: { type: 'object', properties: { x: { type: 'integer' }, y: { type: 'integer' }, w: { type: 'integer' }, h: { type: 'integer' } }, required: ['x', 'y', 'w', 'h'], description: '物理像素区域' },
        scale: { type: 'number', description: '缩放比 0.05-1，默认按 max_width 自动算' },
        max_width: { type: 'integer', description: '输出图最大宽度，默认 1280' },
        diff: { type: 'boolean', description: 'true 时只回变化区' },
      },
    },
  },
  {
    name: 'click',
    description: '在屏幕物理坐标点击。button: left/right/middle，count: 点击次数。',
    inputSchema: { type: 'object', properties: { x: { type: 'integer' }, y: { type: 'integer' }, button: { type: 'string', enum: ['left', 'right', 'middle'] }, count: { type: 'integer' } }, required: ['x', 'y'] },
  },
  {
    name: 'move',
    description: '移动鼠标到物理坐标。',
    inputSchema: { type: 'object', properties: { x: { type: 'integer' }, y: { type: 'integer' } }, required: ['x', 'y'] },
  },
  {
    name: 'drag',
    description: '从一点拖到另一点（物理坐标）。',
    inputSchema: { type: 'object', properties: { from: { type: 'object', properties: { x: { type: 'integer' }, y: { type: 'integer' } }, required: ['x', 'y'] }, to: { type: 'object', properties: { x: { type: 'integer' }, y: { type: 'integer' } }, required: ['x', 'y'] }, button: { type: 'string', enum: ['left', 'right'] } }, required: ['from', 'to'] },
  },
  {
    name: 'scroll',
    description: '在坐标处滚轮。delta 正数向上，负数向下，120 = 一格。',
    inputSchema: { type: 'object', properties: { x: { type: 'integer' }, y: { type: 'integer' }, delta: { type: 'integer' } }, required: ['delta'] },
  },
  {
    name: 'type_text',
    description: '输入文本，支持中文与 emoji（走 SendInput unicode）。',
    inputSchema: { type: 'object', properties: { text: { type: 'string' } }, required: ['text'] },
  },
  {
    name: 'key',
    description: '按键或组合键，如 enter / ctrl+c / alt+tab / win+d / f5。',
    inputSchema: { type: 'object', properties: { key: { type: 'string' } }, required: ['key'] },
  },
  {
    name: 'wait_stable',
    description: '等画面稳定（连续两帧一致）再返回，用于点击后确认。',
    inputSchema: { type: 'object', properties: { timeout_ms: { type: 'integer' }, interval_ms: { type: 'integer' }, region: { type: 'object', properties: { x: { type: 'integer' }, y: { type: 'integer' }, w: { type: 'integer' }, h: { type: 'integer' } }, required: ['x', 'y', 'w', 'h'] } } },
  },
  {
    name: 'window',
    description: '窗口操作。action: list / minimize / maximize / restore / focus / close；title 为标题子串（不区分大小写）。',
    inputSchema: { type: 'object', properties: { action: { type: 'string', enum: ['list', 'minimize', 'maximize', 'restore', 'focus', 'close'] }, title: { type: 'string' } }, required: ['action'] },
  },
];

async function callTool(name, args = {}) {
  const t0 = performance.now();
  let result;
  switch (name) {
    case 'capture': result = await doCapture(args); break;
    case 'click': clickAt(args.x, args.y, args.button ?? 'left', args.count ?? 1); result = { content: [{ type: 'text', text: JSON.stringify({ ok: true, x: args.x, y: args.y }) }] }; break;
    case 'move': cursor(args.x, args.y); result = { content: [{ type: 'text', text: JSON.stringify({ ok: true }) }] }; break;
    case 'drag': {
      cursor(args.from.x, args.from.y);
      const [d, u] = MD[args.button ?? 'left'] ?? MD.left;
      mouse(d);
      const steps = 12;
      for (let i = 1; i <= steps; i++) cursor(args.from.x + ((args.to.x - args.from.x) * i) / steps, args.from.y + ((args.to.y - args.from.y) * i) / steps);
      mouse(u);
      result = { content: [{ type: 'text', text: JSON.stringify({ ok: true }) }] };
      break;
    }
    case 'scroll': {
      if (args.x != null && args.y != null) cursor(args.x, args.y);
      mouse_event(0x0800, 0, 0, args.delta >>> 0, null);
      result = { content: [{ type: 'text', text: JSON.stringify({ ok: true, delta: args.delta }) }] };
      break;
    }
    case 'type_text': typeText(args.text); result = { content: [{ type: 'text', text: JSON.stringify({ ok: true, chars: [...String(args.text)].length }) }] }; break;
    case 'key': pressCombo(args.key); result = { content: [{ type: 'text', text: JSON.stringify({ ok: true, key: args.key }) }] }; break;
    case 'wait_stable': result = await doWaitStable(args); break;
    case 'window': {
      const act = args.action;
      if (act === 'list') {
        result = { content: [{ type: 'text', text: JSON.stringify({ windows: listWindows().slice(0, 40) }) }] };
        break;
      }
      const w = findWindow(args.title);
      const SW = { minimize: 6, maximize: 3, restore: 9 };
      if (act === 'close') { PostMessageW(w.hwnd, 0x0010, null, null); }
      else if (act === 'focus') { if (IsIconic(w.hwnd)) ShowWindow(w.hwnd, 9); SetForegroundWindow(w.hwnd); }
      else { ShowWindow(w.hwnd, SW[act]); }
      result = { content: [{ type: 'text', text: JSON.stringify({ ok: true, action: act, title: w.title }) }] };
      break;
    }
    default: throw new Error('unknown tool: ' + name);
  }
  return { ...result, _ms: Math.round(performance.now() - t0) };
}

// ---------- MCP 循环 ----------
const SERVER = { name: 'screenpilot-fast', version: '0.1.0' };
function reply(id, result, error) {
  const msg = error ? { jsonrpc: '2.0', id, error } : { jsonrpc: '2.0', id, result };
  process.stdout.write(JSON.stringify(msg) + '\n');
}

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on('line', async (line) => {
  const s = line.trim();
  if (!s) return;
  let msg;
  try { msg = JSON.parse(s); } catch { return; }
  const { id, method, params } = msg;
  const isNotification = id === undefined || id === null;
  try {
    if (method === 'initialize') {
      reply(id, {
        protocolVersion: params?.protocolVersion ?? '2024-11-05',
        capabilities: { tools: { listChanged: false } },
        serverInfo: SERVER,
        instructions: 'GDI 截屏 + SendInput 输入，坐标全是屏幕物理像素。capture 返回的 image 坐标要乘 scale 再加 origin 才是物理坐标；diff=true 只回变化区域；点完用 wait_stable 确认。',
      });
    } else if (method === 'notifications/initialized') {
      // no-op
    } else if (method === 'tools/list') {
      reply(id, { tools: TOOLS });
    } else if (method === 'tools/call') {
      const r = await callTool(params?.name, params?.arguments ?? {});
      reply(id, { content: r.content, isError: false });
    } else if (method === 'ping') {
      reply(id, {});
    } else if (method === 'shutdown') {
      reply(id, {});
      process.exit(0);
    } else if (!isNotification) {
      reply(id, null, { code: -32601, message: 'method not found: ' + method });
    }
  } catch (e) {
    if (!isNotification) reply(id, null, { code: -32603, message: String(e?.message ?? e) });
  }
});
process.stderr.write('[screenpilot-fast] ready ' + SCREEN_W + 'x' + SCREEN_H + '\n');
