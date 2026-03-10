'use strict';
/* ═══════════════════════════════════════════════════════════════
   DSA CORE  —  ported from BugTrackerDSA.java
   MaxHeap   → Skip List          (probabilistic multi-level list)
   Trie      → Bug Graph          (adjacency-list keyword graph)
   NQueue    → Circular Queue     (fixed-capacity ring buffer)
   StateMachine → Singly Linked List  (lifecycle chain)
   AuditStack   → Stack               (LIFO, pointer-linked nodes)
═══════════════════════════════════════════════════════════════ */


/* ─────────────────────────────────────────────────────────────
   1. SKIP LIST  —  replaces MaxHeap
   Probabilistic multi-level linked list. Each level is an
   express lane over the level below. Bugs are keyed by priority
   score; highest score = highest priority (tail of sorted list).
   insert / remove  O(log n) average
   peek             O(k) walk from tail
───────────────────────────────────────────────────────────── */
const SEV_RANK = {Critical:4, High:3, Medium:2, Low:1};
const MAX_LEVELS = 8;

class SLNode {
  constructor(score, bug, levels) {
    this.score   = score;
    this.bug     = bug;
    this.forward = new Array(levels).fill(null);
  }
}

class SkipList {
  constructor() {
    this.head          = new SLNode(-Infinity, null, MAX_LEVELS);
    this.currentLevels = 1;
  }

  _score(b) {
    return (SEV_RANK[b.severity] || 0) * 1e5 + (1e5 - b.id);
  }

  _randomLevel() {
    let lvl = 1;
    while (lvl < MAX_LEVELS && Math.random() < 0.5) lvl++;
    return lvl;
  }

  insert(b) {
    const newScore = this._score(b);
    const update   = new Array(MAX_LEVELS).fill(null);
    let   cursor   = this.head;

    for (let i = this.currentLevels - 1; i >= 0; i--) {
      while (cursor.forward[i] && cursor.forward[i].score < newScore)
        cursor = cursor.forward[i];
      update[i] = cursor;
    }

    const newLvl = this._randomLevel();
    if (newLvl > this.currentLevels) {
      for (let i = this.currentLevels; i < newLvl; i++) update[i] = this.head;
      this.currentLevels = newLvl;
    }

    const node = new SLNode(newScore, b, newLvl);
    for (let i = 0; i < newLvl; i++) {
      node.forward[i]      = update[i].forward[i];
      update[i].forward[i] = node;
    }
  }

  remove(bugId) {
    const update = new Array(MAX_LEVELS).fill(null);
    let cursor   = this.head;

    for (let i = this.currentLevels - 1; i >= 0; i--) {
      while (cursor.forward[i] && cursor.forward[i].bug?.id !== bugId)
        cursor = cursor.forward[i];
      update[i] = cursor;
    }

    const target = cursor.forward[0];
    if (!target || target.bug?.id !== bugId) return;

    for (let i = 0; i < this.currentLevels; i++) {
      if (update[i].forward[i] !== target) break;
      update[i].forward[i] = target.forward[i];
    }
  }

  /* peek(n) — same API as old heap.peek(n) */
  peek(n = 5) {
    const all = [];
    let cursor = this.head.forward[0];
    while (cursor) { all.push(cursor.bug); cursor = cursor.forward[0]; }
    return all.reverse().slice(0, n);
  }

  /* rebuild(list) — same API as old heap.rebuild(list) */
  rebuild(bs) {
    this.head.forward.fill(null);
    this.currentLevels = 1;
    bs.forEach(b => this.insert(b));
  }
}


/* ─────────────────────────────────────────────────────────────
   2. BUG GRAPH  —  replaces Trie
   Adjacency-list graph with two node types:
     keyword nodes  ("kw:word")  — one per unique title word
     bug nodes      ("bug:id")   — one per bug
   An edge connects every keyword node to every bug containing
   that word. search() does a BFS from matching keyword nodes
   and collects all bug-node neighbours in one hop.
   insert   O(w)      w = words in title
   search   O(V + E)  BFS
───────────────────────────────────────────────────────────── */
class BugGraph {
  constructor() {
    this.adj = new Map(); // label -> Set<label>
  }

  _kwKey(word) { return 'kw:'  + word.toLowerCase(); }
  _bugKey(id)  { return 'bug:' + id; }

  _addEdge(a, b) {
    if (!this.adj.has(a)) this.adj.set(a, new Set());
    if (!this.adj.has(b)) this.adj.set(b, new Set());
    this.adj.get(a).add(b);
    this.adj.get(b).add(a);
  }

  /* insert(word, id) — same API as old trie.insert(word, id) */
  insert(word, id) {
    const clean = word.toLowerCase().replace(/[^a-z0-9]/g, '');
    if (!clean) return;
    const bugNode = this._bugKey(id);
    if (!this.adj.has(bugNode)) this.adj.set(bugNode, new Set());
    this._addEdge(this._kwKey(clean), bugNode);
  }

  /* search(prefix) — same API as old trie.search(prefix), returns id[] */
  search(prefix) {
    prefix = prefix.toLowerCase();
    const resultIds = new Set();

    for (const node of this.adj.keys()) {
      if (!node.startsWith('kw:')) continue;
      if (!node.slice(3).includes(prefix)) continue;

      const queue   = [node];
      const visited = new Set([node]);
      while (queue.length) {
        const cur = queue.shift();
        for (const nb of (this.adj.get(cur) || [])) {
          if (visited.has(nb)) continue;
          visited.add(nb);
          if (nb.startsWith('bug:')) resultIds.add(parseInt(nb.slice(4)));
        }
      }
    }

    return [...resultIds];
  }
}


/* ─────────────────────────────────────────────────────────────
   3. SINGLY LINKED LIST  —  replaces StateMachine array
   Four StateNodes chained Open → In Progress → Resolved → Closed.
   next() walks forward one node; backwards is structurally
   impossible because links are one-directional.
   next  O(n)
───────────────────────────────────────────────────────────── */
class StateNode {
  constructor(label) { this.label = label; this.next = null; }
}

class LinkedListStateMachine {
  constructor() {
    this.head                    = new StateNode('Open');
    this.head.next               = new StateNode('In Progress');
    this.head.next.next          = new StateNode('Resolved');
    this.head.next.next.next     = new StateNode('Closed');
  }

  /* next(status) — same API as old sm.next(status) */
  next(status) {
    let cursor = this.head;
    while (cursor) {
      if (cursor.label === status) return cursor.next ? cursor.next.label : null;
      cursor = cursor.next;
    }
    return null;
  }
}


/* ─────────────────────────────────────────────────────────────
   4. STACK  —  AuditStack (pointer-linked nodes)
   Each bug gets its own stack. push O(1), list O(n) LIFO.
───────────────────────────────────────────────────────────── */
class StackNode {
  constructor(entry) { this.entry = entry; this.below = null; }
}

class AuditStack {
  constructor() { this.top = null; }

  push(e) {
    const node  = new StackNode({...e, ts: new Date().toISOString()});
    node.below  = this.top;
    this.top    = node;
  }

  list() {
    const result = [];
    let cursor   = this.top;
    while (cursor) { result.push(cursor.entry); cursor = cursor.below; }
    return result; // newest-first (LIFO)
  }
}


/* ─────────────────────────────────────────────────────────────
   5. CIRCULAR QUEUE  —  replaces NQueue
   Fixed-capacity ring buffer. When full the oldest entry is
   silently overwritten. push O(1), markRead / unread / all O(n).
───────────────────────────────────────────────────────────── */
class CircularQueue {
  constructor(capacity = 50) {
    this.ring     = new Array(capacity).fill(null);
    this.capacity = capacity;
    this.head     = 0;
    this.tail     = 0;
    this.count    = 0;
    this.cbs      = [];
  }

  /* push(n) — same API as old nq.push(n) */
  push(n) {
    this.ring[this.tail] = {
      ...n, id: Date.now() + Math.random(), read: false, ts: new Date()
    };
    this.tail = (this.tail + 1) % this.capacity;
    if (this.count < this.capacity) {
      this.count++;
    } else {
      this.head = (this.head + 1) % this.capacity;
    }
    this.cbs.forEach(f => f());
  }

  markRead() {
    for (let i = 0; i < this.count; i++) {
      const n = this.ring[(this.head + i) % this.capacity];
      if (n) n.read = true;
    }
    this.cbs.forEach(f => f());
  }

  clear() {
    this.ring.fill(null);
    this.head = this.tail = this.count = 0;
    this.cbs.forEach(f => f());
  }

  on(f) { this.cbs.push(f); }

  unread() {
    let u = 0;
    for (let i = 0; i < this.count; i++) {
      const n = this.ring[(this.head + i) % this.capacity];
      if (n && !n.read) u++;
    }
    return u;
  }

  /* all() — newest-first, same API as old nq.all() */
  all() {
    const result = [];
    for (let i = this.count - 1; i >= 0; i--) {
      const n = this.ring[(this.head + i) % this.capacity];
      if (n) result.push(n);
    }
    return result;
  }
}


/* ═══ Instances — same names used throughout script.js ═══ */
const heap  = new SkipList();
const sm    = new LinkedListStateMachine();
const nq    = new CircularQueue(50);
const graph = new BugGraph();

/* trie shim — script.js calls trie.insert / trie.search unchanged */
const trie = {
  insert: (w, id) => graph.insert(w, id),
  search: (p)     => graph.search(p)
};

const bugs  = {}, audit = {};
let seq = 1, rData = {}, curView = 'list', loggedUser = 'Inspector';
