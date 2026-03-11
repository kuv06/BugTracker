// =============================================================================
//  BugTrackerDSA.java  —  Pure Java, no external libraries
//
//  Covers all four Course Outcomes (CO1–CO4) applied to the Bug Tracker domain:
//
//  CO1 — Searching & Sorting + Big-O analysis
//        LinearSearch, BinarySearch (on sorted bug arrays)
//        BubbleSort, SelectionSort, InsertionSort, MergeSort, QuickSort
//        (all sort bugs by severity priority score)
//
//  CO2 — Abstract Data Types (Linked Lists)
//        SinglyLinkedList  — bug lifecycle state machine
//        DoublyLinkedList  — bidirectional bug history navigation
//        CircularLinkedList — round-robin developer assignment
//        Operations: insert, delete, search, traverse, reverse, detect cycle
//
//  CO3 — Stacks, Queues, Heaps / Priority Queues
//        Stack (AuditStack)            — LIFO per-bug change history
//        CircularQueue (NQueue)        — fixed-capacity notification ring
//        Deque (BugDeque)              — undo/redo bug actions
//        SkipList (Priority Queue)     — replaces MaxHeap for triage
//
//  CO4 — Hash-Based Data Structures + Java Collections
//        HashTableChaining             — bug index with separate chaining
//        HashTableOpenAddressing       — reporter index with linear probing
//        Java Collections demo         — List, Queue, Deque, Map usage
// =============================================================================

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


// ─────────────────────────────────────────────────────────────────────────────
//  SHARED MODEL
// ─────────────────────────────────────────────────────────────────────────────

class Bug {
    int    id;
    String title;
    String severity;   // Critical | High | Medium | Low
    String status;     // Open | In Progress | Resolved | Closed
    String category;
    String reporter;

    static final Map<String,Integer> RANK = Map.of(
        "Critical",4,"High",3,"Medium",2,"Low",1);

    Bug(int id, String title, String severity,
        String status, String category, String reporter) {
        this.id = id; this.title = title; this.severity = severity;
        this.status = status; this.category = category; this.reporter = reporter;
    }

    int priorityScore() { return RANK.getOrDefault(severity, 0) * 100_000 + (100_000 - id); }

    @Override public String toString() {
        return String.format("BUG-%04d [%-8s] [%-11s] \"%s\"", id, severity, status, title);
    }
}

class AuditEntry {
    final String action, status;
    final Instant ts;
    AuditEntry(String action, String status) {
        this.action = action; this.status = status; this.ts = Instant.now();
    }
    @Override public String toString() { return "["+ts+"] "+action+" → "+status; }
}


// =============================================================================
//  CO1 — SEARCHING & SORTING ALGORITHMS
//  All algorithms operate on Bug[] sorted/searched by priorityScore().
//  Big-O complexities annotated on every method.
// =============================================================================

class SearchSort {

    // ── LINEAR SEARCH  O(n) time, O(1) space ─────────────────────────────
    // Scans every element. Works on unsorted arrays.
    // Used to find a bug by id when no index is available.
    public static int linearSearch(Bug[] bugs, int targetId) {
        for (int i = 0; i < bugs.length; i++)
            if (bugs[i].id == targetId) return i;
        return -1;  // not found
    }

    // ── BINARY SEARCH  O(log n) time, O(1) space ─────────────────────────
    // Requires array sorted ascending by priorityScore().
    // Halves the search space each step — much faster for large lists.
    public static int binarySearch(Bug[] sortedBugs, int targetScore) {
        int lo = 0, hi = sortedBugs.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            int s   = sortedBugs[mid].priorityScore();
            if      (s == targetScore) return mid;
            else if (s  < targetScore) lo = mid + 1;
            else                       hi = mid - 1;
        }
        return -1;
    }

    // ── BUBBLE SORT  O(n²) time, O(1) space ──────────────────────────────
    // Repeatedly swaps adjacent elements that are out of order.
    // Simple but inefficient — useful only for small or nearly-sorted input.
    public static void bubbleSort(Bug[] bugs) {
        int n = bugs.length;
        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < n - i - 1; j++) {
                if (bugs[j].priorityScore() < bugs[j+1].priorityScore()) {
                    Bug tmp = bugs[j]; bugs[j] = bugs[j+1]; bugs[j+1] = tmp;
                    swapped = true;
                }
            }
            if (!swapped) break; // early exit: already sorted
        }
    }

    // ── SELECTION SORT  O(n²) time, O(1) space ───────────────────────────
    // Finds the maximum element and places it at the front each pass.
    // Minimises swaps (exactly n-1) — good when writes are expensive.
    public static void selectionSort(Bug[] bugs) {
        int n = bugs.length;
        for (int i = 0; i < n - 1; i++) {
            int maxIdx = i;
            for (int j = i + 1; j < n; j++)
                if (bugs[j].priorityScore() > bugs[maxIdx].priorityScore())
                    maxIdx = j;
            Bug tmp = bugs[i]; bugs[i] = bugs[maxIdx]; bugs[maxIdx] = tmp;
        }
    }

    // ── INSERTION SORT  O(n²) worst, O(n) best, O(1) space ───────────────
    // Builds sorted portion left-to-right; efficient on nearly-sorted data.
    // Used for small sub-arrays inside hybrid sorts (e.g. TimSort).
    public static void insertionSort(Bug[] bugs) {
        for (int i = 1; i < bugs.length; i++) {
            Bug key = bugs[i];
            int j   = i - 1;
            while (j >= 0 && bugs[j].priorityScore() < key.priorityScore()) {
                bugs[j+1] = bugs[j];
                j--;
            }
            bugs[j+1] = key;
        }
    }

    // ── MERGE SORT  O(n log n) time, O(n) space ───────────────────────────
    // Divide-and-conquer: split in half, sort each, merge back.
    // Stable sort — bugs with equal priority keep their original order.
    public static void mergeSort(Bug[] bugs, int left, int right) {
        if (left >= right) return;
        int mid = left + (right - left) / 2;
        mergeSort(bugs, left, mid);
        mergeSort(bugs, mid + 1, right);
        merge(bugs, left, mid, right);
    }

    private static void merge(Bug[] bugs, int left, int mid, int right) {
        int n1 = mid - left + 1, n2 = right - mid;
        Bug[] L = new Bug[n1], R = new Bug[n2];
        System.arraycopy(bugs, left,     L, 0, n1);
        System.arraycopy(bugs, mid + 1,  R, 0, n2);
        int i = 0, j = 0, k = left;
        while (i < n1 && j < n2)
            bugs[k++] = L[i].priorityScore() >= R[j].priorityScore() ? L[i++] : R[j++];
        while (i < n1) bugs[k++] = L[i++];
        while (j < n2) bugs[k++] = R[j++];
    }

    // ── QUICK SORT  O(n log n) avg, O(n²) worst, O(log n) space ─────────
    // Partition around pivot, sort partitions recursively.
    // In-place and cache-friendly — fastest in practice for large arrays.
    public static void quickSort(Bug[] bugs, int low, int high) {
        if (low < high) {
            int pi = partition(bugs, low, high);
            quickSort(bugs, low,    pi - 1);
            quickSort(bugs, pi + 1, high);
        }
    }

    private static int partition(Bug[] bugs, int low, int high) {
        int pivot = bugs[high].priorityScore();
        int i     = low - 1;
        for (int j = low; j < high; j++) {
            if (bugs[j].priorityScore() >= pivot) {
                i++;
                Bug tmp = bugs[i]; bugs[i] = bugs[j]; bugs[j] = tmp;
            }
        }
        Bug tmp = bugs[i+1]; bugs[i+1] = bugs[high]; bugs[high] = tmp;
        return i + 1;
    }
}


// =============================================================================
//  CO2 — ABSTRACT DATA TYPES: LINKED LISTS
// =============================================================================

// ── SINGLY LINKED LIST  —  Bug Lifecycle State Machine ───────────────────────
//  Operations: insert, delete, search, traverse  O(n)  |  reverse  O(n)
//  Used as: linear state chain Open→In Progress→Resolved→Closed
class SLLNode { String label; SLLNode next; SLLNode(String l){label=l;} }

class SinglyLinkedList {
    SLLNode head = null;

    public void append(String label) {          // insert at tail  O(n)
        if (head == null) { head = new SLLNode(label); return; }
        SLLNode c = head;
        while (c.next != null) c = c.next;
        c.next = new SLLNode(label);
    }

    public boolean delete(String label) {       // delete by value  O(n)
        if (head == null) return false;
        if (head.label.equals(label)) { head = head.next; return true; }
        SLLNode c = head;
        while (c.next != null) {
            if (c.next.label.equals(label)) { c.next = c.next.next; return true; }
            c = c.next;
        }
        return false;
    }

    public boolean search(String label) {       // search  O(n)
        SLLNode c = head;
        while (c != null) { if (c.label.equals(label)) return true; c = c.next; }
        return false;
    }

    public List<String> traverse() {            // traverse  O(n)
        List<String> r = new ArrayList<>();
        SLLNode c = head;
        while (c != null) { r.add(c.label); c = c.next; }
        return r;
    }

    public void reverse() {                     // reverse in-place  O(n)
        SLLNode prev = null, cur = head;
        while (cur != null) { SLLNode nx = cur.next; cur.next = prev; prev = cur; cur = nx; }
        head = prev;
    }

    // next(status) — walk one step forward  O(n)
    public String next(String status) {
        SLLNode c = head;
        while (c != null) {
            if (c.label.equals(status)) return c.next != null ? c.next.label : null;
            c = c.next;
        }
        return null;
    }
}


// ── DOUBLY LINKED LIST  —  Bidirectional Bug History Navigation ───────────────
//  Operations: insertFront, insertBack, deleteFront, deleteBack,
//              traverseForward, traverseBackward, search, reverse  O(n)
//  Used as: ordered list of recently viewed bugs (navigate forward & back)
class DLLNode {
    Bug data; DLLNode prev, next;
    DLLNode(Bug b) { data = b; }
}

class DoublyLinkedList {
    DLLNode head = null, tail = null;
    int size = 0;

    public void insertFront(Bug b) {
        DLLNode n = new DLLNode(b);
        if (head == null) { head = tail = n; }
        else { n.next = head; head.prev = n; head = n; }
        size++;
    }

    public void insertBack(Bug b) {
        DLLNode n = new DLLNode(b);
        if (tail == null) { head = tail = n; }
        else { tail.next = n; n.prev = tail; tail = n; }
        size++;
    }

    public Bug deleteFront() {
        if (head == null) return null;
        Bug b = head.data;
        head = head.next;
        if (head != null) head.prev = null; else tail = null;
        size--;
        return b;
    }

    public Bug deleteBack() {
        if (tail == null) return null;
        Bug b = tail.data;
        tail = tail.prev;
        if (tail != null) tail.next = null; else head = null;
        size--;
        return b;
    }

    public boolean search(int bugId) {
        DLLNode c = head;
        while (c != null) { if (c.data.id == bugId) return true; c = c.next; }
        return false;
    }

    public List<Bug> traverseForward() {
        List<Bug> r = new ArrayList<>();
        DLLNode c = head;
        while (c != null) { r.add(c.data); c = c.next; }
        return r;
    }

    public List<Bug> traverseBackward() {
        List<Bug> r = new ArrayList<>();
        DLLNode c = tail;
        while (c != null) { r.add(c.data); c = c.prev; }
        return r;
    }

    public void reverse() {           // swap prev/next on every node  O(n)
        DLLNode c = head;
        while (c != null) { DLLNode tmp = c.prev; c.prev = c.next; c.next = tmp; c = c.prev; }
        DLLNode tmp = head; head = tail; tail = tmp;
    }
}


// ── CIRCULAR LINKED LIST  —  Round-Robin Developer Assignment ────────────────
//  Operations: insert, delete, traverse, detectCycle  O(n)
//  Tail.next always points back to head — the list never terminates.
//  Used as: rotating assignment queue so workload stays balanced.
class CLLNode { String dev; CLLNode next; CLLNode(String d){dev=d;} }

class CircularLinkedList {
    CLLNode tail = null;     // tail.next == head

    public void insert(String dev) {
        CLLNode n = new CLLNode(dev);
        if (tail == null) { n.next = n; tail = n; }
        else { n.next = tail.next; tail.next = n; tail = n; }
    }

    public boolean delete(String dev) {
        if (tail == null) return false;
        CLLNode cur = tail.next, prev = tail;
        do {
            if (cur.dev.equals(dev)) {
                if (cur == tail && cur.next == cur) { tail = null; return true; }
                prev.next = cur.next;
                if (cur == tail) tail = prev;
                return true;
            }
            prev = cur; cur = cur.next;
        } while (cur != tail.next);
        return false;
    }

    public List<String> traverse() {
        List<String> r = new ArrayList<>();
        if (tail == null) return r;
        CLLNode c = tail.next;
        do { r.add(c.dev); c = c.next; } while (c != tail.next);
        return r;
    }

    // nextAssignee — rotate tail forward one step  O(1)
    public String nextAssignee() {
        if (tail == null) return "Unassigned";
        tail = tail.next;
        return tail.dev;
    }

    // detectCycle — Floyd's tortoise-and-hare  O(n) time, O(1) space
    // For a CircularLinkedList the cycle always exists;
    // this method is also used to verify integrity after deletions.
    public boolean detectCycle() {
        if (tail == null) return false;
        CLLNode slow = tail.next, fast = tail.next;
        do {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) return true;
        } while (fast != tail.next && fast.next != tail.next);
        return false;
    }
}


// =============================================================================
//  CO3 — STACKS, QUEUES, DEQUE, SKIP LIST (PRIORITY QUEUE)
// =============================================================================

// ── STACK  —  AuditStack: LIFO per-bug change history ────────────────────────
//  push O(1)  |  pop O(1)  |  peek O(1)  |  list O(n)
class StackNode { AuditEntry entry; StackNode below; StackNode(AuditEntry e){entry=e;} }

class AuditStack {
    private StackNode top = null;
    private int size = 0;

    public void push(String action, String status) {
        StackNode n = new StackNode(new AuditEntry(action, status));
        n.below = top; top = n; size++;
    }

    public AuditEntry pop()  { if(top==null)return null; AuditEntry e=top.entry; top=top.below; size--; return e; }
    public AuditEntry peek() { return top != null ? top.entry : null; }
    public int  size()       { return size; }
    public boolean isEmpty() { return top == null; }

    public List<AuditEntry> list() {
        List<AuditEntry> r = new ArrayList<>();
        StackNode c = top;
        while (c != null) { r.add(c.entry); c = c.below; }
        return r;   // newest-first
    }
}


// ── CIRCULAR QUEUE  —  NQueue: fixed-capacity notification ring ───────────────
//  enqueue O(1)  |  dequeue O(1)  |  full overwrites oldest
class Notification {
    final String title, message; final Instant ts; boolean read;
    Notification(String t, String m) { title=t; message=m; ts=Instant.now(); }
    @Override public String toString() { return "["+(read?"READ  ":"UNREAD")+"] "+title+" — "+message; }
}

class CircularQueue {
    private final Notification[] ring;
    private final int capacity;
    private int head=0, tail=0, count=0;

    CircularQueue(int capacity) { this.capacity=capacity; ring=new Notification[capacity]; }

    public void enqueue(String title, String msg) {
        ring[tail] = new Notification(title, msg);
        tail = (tail + 1) % capacity;
        if (count < capacity) count++;
        else head = (head + 1) % capacity;   // overwrite oldest
    }

    public Notification dequeue() {
        if (count == 0) return null;
        Notification n = ring[head]; ring[head] = null;
        head = (head + 1) % capacity; count--;
        return n;
    }

    public void markAllRead() {
        for (int i=0;i<count;i++) { Notification n=ring[(head+i)%capacity]; if(n!=null)n.read=true; }
    }

    public int unreadCount() {
        int u=0;
        for (int i=0;i<count;i++) { Notification n=ring[(head+i)%capacity]; if(n!=null&&!n.read)u++; }
        return u;
    }

    public List<Notification> all() {
        List<Notification> r = new ArrayList<>();
        for (int i=count-1;i>=0;i--) { Notification n=ring[(head+i)%capacity]; if(n!=null)r.add(n); }
        return r;   // newest-first
    }

    public void clear() { Arrays.fill(ring,null); head=tail=count=0; }
    public int  size()  { return count; }
}


// ── DEQUE  —  BugDeque: double-ended queue for undo/redo ─────────────────────
//  addFront/addBack O(1)  |  removeFront/removeBack O(1)
//  Used as: undo queue — new actions go to back; undo pops from back;
//           redo pushes to front for replay.
class DequeNode { Bug data; DequeNode prev, next; DequeNode(Bug b){data=b;} }

class BugDeque {
    private DequeNode head=null, tail=null;
    private int size=0;

    public void addFront(Bug b) {
        DequeNode n=new DequeNode(b);
        if(head==null){head=tail=n;}
        else{n.next=head;head.prev=n;head=n;}
        size++;
    }

    public void addBack(Bug b) {
        DequeNode n=new DequeNode(b);
        if(tail==null){head=tail=n;}
        else{tail.next=n;n.prev=tail;tail=n;}
        size++;
    }

    public Bug removeFront() {
        if(head==null)return null;
        Bug b=head.data; head=head.next;
        if(head!=null)head.prev=null; else tail=null;
        size--; return b;
    }

    public Bug removeBack() {
        if(tail==null)return null;
        Bug b=tail.data; tail=tail.prev;
        if(tail!=null)tail.next=null; else head=null;
        size--; return b;
    }

    public Bug peekFront() { return head!=null?head.data:null; }
    public Bug peekBack()  { return tail!=null?tail.data:null; }
    public int size()      { return size; }
    public boolean isEmpty(){ return size==0; }
}


// ── SKIP LIST  —  Priority Queue for bug triage ───────────────────────────────
//  Probabilistic multi-level linked list.
//  insert O(log n) avg  |  peekTop O(k)  |  remove O(log n) avg
//  Compared to heap: same complexity, different mechanism — layered
//  forward pointers, no array indexing, no parent/child sifting.
class SLNode {
    double score; Bug bug; SLNode[] forward;
    SLNode(double s, Bug b, int lvls) { score=s; bug=b; forward=new SLNode[lvls]; }
}

class SkipListPQ {
    private static final int MAX_LVL = 8;
    private final SLNode head = new SLNode(Double.NEGATIVE_INFINITY, null, MAX_LVL);
    private int curLvls = 1;
    private final Random rng = new Random(42);

    private double score(Bug b) {
        return Bug.RANK.getOrDefault(b.severity,0)*100_000.0 + (100_000-b.id);
    }

    private int randLvl() {
        int l=1; while(l<MAX_LVL&&rng.nextDouble()<0.5)l++; return l;
    }

    public void insert(Bug b) {
        double sc=score(b); SLNode[] upd=new SLNode[MAX_LVL]; SLNode cur=head;
        for(int i=curLvls-1;i>=0;i--){
            while(cur.forward[i]!=null&&cur.forward[i].score<sc)cur=cur.forward[i];
            upd[i]=cur;
        }
        int nl=randLvl();
        if(nl>curLvls){for(int i=curLvls;i<nl;i++)upd[i]=head;curLvls=nl;}
        SLNode n=new SLNode(sc,b,nl);
        for(int i=0;i<nl;i++){n.forward[i]=upd[i].forward[i];upd[i].forward[i]=n;}
    }

    public void rebuild(List<Bug> bugs) {
        Arrays.fill(head.forward,null); curLvls=1;
        bugs.forEach(this::insert);
    }

    public List<Bug> peekTop(int n) {
        List<Bug> all=new ArrayList<>();
        SLNode c=head.forward[0]; while(c!=null){all.add(c.bug);c=c.forward[0];}
        Collections.reverse(all);
        return all.subList(0,Math.min(n,all.size()));
    }
}


// =============================================================================
//  CO4 — HASH-BASED DATA STRUCTURES
// =============================================================================

// ── HASH TABLE WITH SEPARATE CHAINING ─────────────────────────────────────────
//  Each bucket holds a linked list of entries (chaining resolves collisions).
//  insert O(1) avg  |  get O(1) avg  |  worst O(n) if all keys hash same bucket
//  Used as: primary bug index (id → Bug)
class HashTableChaining {
    private static final int BUCKETS = 16;

    private static class Entry {
        int key; Bug value; Entry next;
        Entry(int k, Bug v) { key=k; value=v; }
    }

    @SuppressWarnings("unchecked")
    private final Entry[] table = new Entry[BUCKETS];

    private int hash(int key) { return Math.abs(key % BUCKETS); }

    public void put(int key, Bug value) {
        int idx = hash(key);
        Entry e = table[idx];
        while (e != null) { if (e.key == key) { e.value = value; return; } e = e.next; }
        Entry n = new Entry(key, value); n.next = table[idx]; table[idx] = n;  // prepend to chain
    }

    public Bug get(int key) {
        Entry e = table[hash(key)];
        while (e != null) { if (e.key == key) return e.value; e = e.next; }
        return null;
    }

    public boolean remove(int key) {
        int idx = hash(key); Entry e = table[idx], prev = null;
        while (e != null) {
            if (e.key == key) {
                if (prev == null) table[idx] = e.next; else prev.next = e.next;
                return true;
            }
            prev = e; e = e.next;
        }
        return false;
    }

    public int size() {
        int s=0; for(Entry e:table){Entry c=e;while(c!=null){s++;c=c.next;}} return s;
    }
}


// ── HASH TABLE WITH OPEN ADDRESSING (linear probing) ─────────────────────────
//  On collision, probe forward until an empty slot is found.
//  insert O(1) avg  |  get O(1) avg  |  no extra memory for chains
//  Used as: reporter name → bug count index (fast reporter lookup)
class HashTableOpenAddressing {
    private static final int CAPACITY = 32;
    private static final String DELETED = "__DELETED__";

    private final String[] keys   = new String[CAPACITY];
    private final int[]    values = new int[CAPACITY];

    private int hash(String key) {
        int h = 0;
        for (char c : key.toCharArray()) h = (h * 31 + c) % CAPACITY;
        return Math.abs(h);
    }

    public void put(String key, int value) {
        int idx = hash(key);
        while (keys[idx] != null && !keys[idx].equals(DELETED) && !keys[idx].equals(key))
            idx = (idx + 1) % CAPACITY;   // linear probe
        keys[idx] = key; values[idx] = value;
    }

    public int get(String key) {
        int idx = hash(key);
        while (keys[idx] != null) {
            if (keys[idx].equals(key)) return values[idx];
            idx = (idx + 1) % CAPACITY;
        }
        return -1;  // not found
    }

    public void increment(String key) { put(key, Math.max(0, get(key)) + 1); }

    public boolean remove(String key) {
        int idx = hash(key);
        while (keys[idx] != null) {
            if (keys[idx].equals(key)) { keys[idx] = DELETED; values[idx] = 0; return true; }
            idx = (idx + 1) % CAPACITY;
        }
        return false;
    }
}




public class BugTracker {

    static Bug[] seedBugs() {
        return new Bug[]{
            new Bug(1,  "Login crash on iOS 17",                "Critical", "Open",        "UI / Frontend", "Akash Singh"),
            new Bug(2,  "Payment gateway timeout",              "Critical", "In Progress",  "API / Backend", "Sneha Reddy"),
            new Bug(3,  "Dashboard charts empty new accounts",  "High",     "In Progress",  "UI / Frontend", "Rohan Das"),
            new Bug(4,  "Search fails for unicode names",       "High",     "Open",         "API / Backend", "Priya Nair"),
            new Bug(5,  "Password reset emails land in spam",   "Medium",   "Open",         "API / Backend", "Vikram Joshi"),
            new Bug(6,  "Dark mode preference not persisted",   "Medium",   "Resolved",     "UI / Frontend", "Ananya Kapoor"),
            new Bug(7,  "Avatar upload silently fails over 2MB","Medium",   "Open",         "UI / Frontend", "Karan Mehta"),
            new Bug(8,  "CSV export omits final row",           "Low",      "Closed",       "API / Backend", "Divya Sharma"),
            new Bug(9,  "Tooltip clipped on 375px screens",     "Low",      "Open",         "UI / Frontend", "Manish Patel"),
            new Bug(10, "Bulk delete shows duplicate dialog",   "Low",      "Resolved",     "UI / Frontend", "Rahul Kumar"),
        };
    }

    static void banner(String title) {
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.printf( "║  %-40s║%n", title);
        System.out.println("╚══════════════════════════════════════════╝");
    }

    static void runDSADemo(String[] args) {

        Bug[] bugs = seedBugs();

        // ══════════════════════════════════════════════════════════════════
        //  CO1 — SEARCHING & SORTING
        // ══════════════════════════════════════════════════════════════════

        banner("CO1 — LINEAR SEARCH  O(n)");
        int idx = SearchSort.linearSearch(bugs, 5);
        System.out.println("  Search bugId=5 → index " + idx + " → " + bugs[idx]);

        banner("CO1 — BUBBLE SORT  O(n²)");
        Bug[] arr = seedBugs();
        SearchSort.bubbleSort(arr);
        System.out.println("  Top 3 after BubbleSort:");
        for (int i=0;i<3;i++) System.out.println("    " + arr[i]);

        banner("CO1 — SELECTION SORT  O(n²)");
        arr = seedBugs();
        SearchSort.selectionSort(arr);
        System.out.println("  Top 3 after SelectionSort:");
        for (int i=0;i<3;i++) System.out.println("    " + arr[i]);

        banner("CO1 — INSERTION SORT  O(n²) / O(n) best");
        arr = seedBugs();
        SearchSort.insertionSort(arr);
        System.out.println("  Top 3 after InsertionSort:");
        for (int i=0;i<3;i++) System.out.println("    " + arr[i]);

        banner("CO1 — MERGE SORT  O(n log n)");
        arr = seedBugs();
        SearchSort.mergeSort(arr, 0, arr.length-1);
        System.out.println("  Top 3 after MergeSort:");
        for (int i=0;i<3;i++) System.out.println("    " + arr[i]);

        banner("CO1 — QUICK SORT  O(n log n) avg");
        arr = seedBugs();
        SearchSort.quickSort(arr, 0, arr.length-1);
        System.out.println("  Top 3 after QuickSort:");
        for (int i=0;i<3;i++) System.out.println("    " + arr[i]);

        banner("CO1 — BINARY SEARCH  O(log n)  [needs sorted array]");
        arr = seedBugs();
        SearchSort.mergeSort(arr, 0, arr.length-1);
        int targetScore = bugs[0].priorityScore();   // BUG-1 Critical
        int found = SearchSort.binarySearch(arr, targetScore);
        System.out.println("  Search score="+targetScore+" → index "+found+" → "+arr[found]);

        // ══════════════════════════════════════════════════════════════════
        //  CO2 — LINKED LISTS
        // ══════════════════════════════════════════════════════════════════

        banner("CO2 — SINGLY LINKED LIST (State Machine)");
        SinglyLinkedList sm = new SinglyLinkedList();
        for (String s : new String[]{"Open","In Progress","Resolved","Closed"}) sm.append(s);
        System.out.println("  Traverse : " + sm.traverse());
        System.out.println("  next(Open) = " + sm.next("Open"));
        System.out.println("  next(Resolved) = " + sm.next("Resolved"));
        sm.reverse();
        System.out.println("  After reverse: " + sm.traverse());
        sm.delete("Open");
        System.out.println("  After delete('Open'): " + sm.traverse());
        System.out.println("  search('Resolved') = " + sm.search("Resolved"));

        banner("CO2 — DOUBLY LINKED LIST (Bug History Navigation)");
        DoublyLinkedList dll = new DoublyLinkedList();
        for (Bug b : bugs) dll.insertBack(b);
        System.out.println("  Forward  first 3: " + dll.traverseForward().subList(0,3));
        System.out.println("  Backward first 3: " + dll.traverseBackward().subList(0,3));
        dll.reverse();
        System.out.println("  After reverse, forward first 3: " + dll.traverseForward().subList(0,3));
        System.out.println("  search(bugId=7) = " + dll.search(7));
        dll.deleteFront(); dll.deleteBack();
        System.out.println("  After deleteFront+deleteBack, size = " + dll.size);

        banner("CO2 — CIRCULAR LINKED LIST (Round-Robin Assignment)");
        CircularLinkedList cll = new CircularLinkedList();
        for (String d : new String[]{"Dev A","Dev B","Dev C"}) cll.insert(d);
        System.out.println("  Traverse: " + cll.traverse());
        System.out.println("  Next 4 assignments: " +
            cll.nextAssignee()+", "+cll.nextAssignee()+", "+
            cll.nextAssignee()+", "+cll.nextAssignee());
        System.out.println("  Cycle detected: " + cll.detectCycle());
        cll.delete("Dev B");
        System.out.println("  After delete Dev B: " + cll.traverse());

        // ══════════════════════════════════════════════════════════════════
        //  CO3 — STACK, CIRCULAR QUEUE, DEQUE, SKIP LIST
        // ══════════════════════════════════════════════════════════════════

        banner("CO3 — STACK (AuditStack — LIFO)");
        Map<Integer,AuditStack> audits = new HashMap<>();
        for (Bug b : bugs) {
            AuditStack st = new AuditStack();
            st.push("Filed by " + b.reporter, "Open");
            if (b.status.equals("In Progress")) st.push("Moved to In Progress", "In Progress");
            if (b.status.equals("Resolved"))  { st.push("Moved to In Progress","In Progress"); st.push("Marked Resolved","Resolved"); }
            if (b.status.equals("Closed"))    { st.push("Moved to In Progress","In Progress"); st.push("Marked Resolved","Resolved"); st.push("Closed","Closed"); }
            audits.put(b.id, st);
        }
        System.out.println("  Audit trail for BUG-0002 (newest first):");
        audits.get(2).list().forEach(e -> System.out.println("    " + e));
        System.out.println("  Peek (latest): " + audits.get(2).peek());
        System.out.println("  Stack size: " + audits.get(2).size());

        banner("CO3 — CIRCULAR QUEUE (Notifications)");
        CircularQueue cq = new CircularQueue(5);  // capacity 5 to demo overwrite
        for (Bug b : bugs) cq.enqueue("New Filing: BUG-"+String.format("%04d",b.id), b.title);
        System.out.println("  Size (capped at 5): " + cq.size());
        System.out.println("  Unread: " + cq.unreadCount());
        cq.markAllRead();
        System.out.println("  After markAllRead, unread: " + cq.unreadCount());
        cq.enqueue("Critical Alert","BUG-0001 unresolved in Production");
        System.out.println("  Latest: " + cq.all().get(0));

        banner("CO3 — DEQUE (Undo/Redo Bug Actions)");
        BugDeque dq = new BugDeque();
        dq.addBack(bugs[0]); dq.addBack(bugs[1]); dq.addBack(bugs[2]);
        System.out.println("  PeekFront: " + dq.peekFront());
        System.out.println("  PeekBack : " + dq.peekBack());
        dq.addFront(bugs[3]);
        System.out.println("  After addFront(BUG-4), peekFront: " + dq.peekFront());
        Bug undone = dq.removeBack();
        System.out.println("  Undo (removeBack): " + undone);
        System.out.println("  Deque size: " + dq.size());

        banner("CO3 — SKIP LIST PRIORITY QUEUE  O(log n)");
        SkipListPQ pq = new SkipListPQ();
        for (Bug b : bugs)
            if (b.status.equals("Open") || b.status.equals("In Progress")) pq.insert(b);
        System.out.println("  Top 5 active bugs by priority:");
        pq.peekTop(5).forEach(b -> System.out.println("    " + b));

        // ══════════════════════════════════════════════════════════════════
        //  CO4 — HASH TABLES + JAVA COLLECTIONS
        // ══════════════════════════════════════════════════════════════════

        banner("CO4 — HASH TABLE: SEPARATE CHAINING (Bug Index)");
        HashTableChaining htc = new HashTableChaining();
        for (Bug b : bugs) htc.put(b.id, b);
        System.out.println("  get(3)  → " + htc.get(3));
        System.out.println("  get(7)  → " + htc.get(7));
        System.out.println("  size()  → " + htc.size());
        htc.remove(3);
        System.out.println("  After remove(3), get(3) → " + htc.get(3));

        banner("CO4 — HASH TABLE: OPEN ADDRESSING (Reporter Bug Count)");
        HashTableOpenAddressing htoa = new HashTableOpenAddressing();
        for (Bug b : bugs) htoa.increment(b.reporter);
        System.out.println("  Akash Singh bug count  → " + htoa.get("Akash Singh"));
        System.out.println("  Sneha Reddy bug count  → " + htoa.get("Sneha Reddy"));
        htoa.remove("Akash Singh");
        System.out.println("  After remove Akash, get → " + htoa.get("Akash Singh"));

        banner("CO4 — JAVA COLLECTIONS: List, Queue, Deque, Map");

        // List<Bug> — ArrayList backed, sort by severity
        List<Bug> bugList = new ArrayList<>(Arrays.asList(bugs));
        bugList.sort((a,b2) -> Integer.compare(b2.priorityScore(), a.priorityScore()));
        System.out.println("  List sorted top-2: " + bugList.subList(0,2));

        // Queue<Bug> — LinkedList as FIFO triage intake
        Queue<Bug> intake = new LinkedList<>(bugList);
        System.out.println("  Queue.poll() → " + intake.poll());
        System.out.println("  Queue.peek() → " + intake.peek());

        // Deque<Bug> — ArrayDeque as undo buffer
        Deque<Bug> undoBuffer = new ArrayDeque<>();
        bugList.forEach(undoBuffer::push);
        System.out.println("  Deque.push/pop (undo): popped → " + undoBuffer.pop());
        System.out.println("  Deque size: " + undoBuffer.size());

        // Map<String,List<Bug>> — group bugs by severity
        Map<String,List<Bug>> bySeverity = new HashMap<>();
        for (Bug b : bugs)
            bySeverity.computeIfAbsent(b.severity, k -> new ArrayList<>()).add(b);
        bySeverity.forEach((sev, list) ->
            System.out.println("  " + sev + " (" + list.size() + "): " +
                list.stream().map(b2 -> "BUG-"+String.format("%04d",b2.id))
                    .collect(Collectors.joining(", "))));
    }

    // =========================================================================
    //  ACCOUNTS  { username, password, role, displayName }
    // =========================================================================
    private static final String[][] ACCOUNTS = {
        { "admin",   "admin123", "developer", "Admin User"   },
        { "tester",  "test123",  "tester",    "Test User"    },
        { "user1",   "user123",  "user",      "Regular User" },
    };

    private static final Scanner loginSc = new Scanner(System.in);
    private static String loggedUser = null;
    private static String userRole   = null;

    // DSA objects wired to seed data
    private static final Map<Integer, AuditStack> audits  = new HashMap<>();
    private static final CircularQueue            notifQ  = new CircularQueue(50);
    private static final SkipListPQ               pq      = new SkipListPQ();
    private static final SinglyLinkedList         smChain = new SinglyLinkedList();
    private static final HashTableChaining        htc     = new HashTableChaining();
    private static final HashTableOpenAddressing  htoa    = new HashTableOpenAddressing();

    static {
        for (String s : new String[]{"Open","In Progress","Resolved","Closed"})
            smChain.append(s);

        for (Bug b : seedBugs()) {
            htc.put(b.id, b);
            htoa.increment(b.reporter);

            AuditStack as = new AuditStack();
            as.push("Filed by " + b.reporter, "Open");
            if (b.status.equals("In Progress")) as.push("Moved to In Progress", "In Progress");
            if (b.status.equals("Resolved")) {
                as.push("Moved to In Progress", "In Progress");
                as.push("Marked Resolved", "Resolved");
            }
            if (b.status.equals("Closed")) {
                as.push("Moved to In Progress", "In Progress");
                as.push("Marked Resolved", "Resolved");
                as.push("Closed", "Closed");
            }
            audits.put(b.id, as);

            if (b.status.equals("Open") || b.status.equals("In Progress"))
                pq.insert(b);
        }
    }

    // =========================================================================
    //  LOGIN
    // =========================================================================
    private static boolean doLogin() {
        System.out.println("\n=== Bug Tracker — Login ===");
        System.out.println("Demo: admin/admin123  tester/test123  user1/user123\n");
        System.out.print("Username: "); String u = loginSc.nextLine().trim();
        System.out.print("Password: "); String p = loginSc.nextLine().trim();

        for (String[] acc : ACCOUNTS) {
            if (acc[0].equals(u) && acc[1].equals(p)) {
                loggedUser = acc[3]; userRole = acc[2];
                System.out.println("\nWelcome, " + loggedUser + " [" + userRole + "]");
                notifQ.enqueue("Login", "Welcome back, " + loggedUser);
                return true;
            }
        }
        System.out.println("Invalid credentials."); return false;
    }

    // =========================================================================
    //  MAIN MENU
    // =========================================================================
    private static void mainMenu() {
        while (true) {
            System.out.println("\n=== Menu [" + loggedUser + " / " + userRole + "] ===");
            if (userRole.equals("developer")) {
                System.out.println("1. View all bugs");
                System.out.println("2. File a new bug");
                System.out.println("3. Update bug status");
                System.out.println("4. View audit trail");
                System.out.println("5. Top-priority bugs (Skip List)");
                System.out.println("6. Run full DSA demo");
                System.out.println("7. Notifications");
            } else if (userRole.equals("tester")) {
                System.out.println("1. View all bugs");
                System.out.println("2. File a new bug");
                System.out.println("4. View audit trail");
                System.out.println("7. Notifications");
            } else {
                System.out.println("2. File a new bug");
                System.out.println("7. Notifications");
            }
            System.out.println("0. Logout");
            System.out.print("\nChoice: ");
            String ch = loginSc.nextLine().trim();
            switch (ch) {
                case "1" -> { if (!userRole.equals("user")) viewAllBugs(); }
                case "2" -> fileNewBug();
                case "3" -> { if (userRole.equals("developer")) updateStatus(); }
                case "4" -> { if (!userRole.equals("user")) viewAudit(); }
                case "5" -> { if (userRole.equals("developer")) viewTopPriority(); }
                case "6" -> { if (userRole.equals("developer")) runDSADemo(new String[]{}); }
                case "7" -> viewNotifications();
                case "0" -> { System.out.println("Logged out."); return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    // =========================================================================
    //  SCREENS
    // =========================================================================
    private static void viewAllBugs() {
        System.out.println("\n--- All Bugs ---");
        System.out.printf("%-6s  %-10s  %-13s  %-40s  %s%n", "ID","Severity","Status","Title","Reporter");
        System.out.println("-".repeat(90));
        for (Bug b : seedBugs())
            System.out.printf("%-6d  %-10s  %-13s  %-40s  %s%n",
                b.id, b.severity, htc.get(b.id).status, b.title, b.reporter);
    }

    private static void fileNewBug() {
        System.out.println("\n--- File New Bug ---");
        System.out.print("Title: "); String title = loginSc.nextLine().trim();
        if (title.isEmpty()) { System.out.println("Title required."); return; }
        System.out.println("Severity: 1=Critical  2=High  3=Medium  4=Low");
        System.out.print("Choice: ");
        String sev = switch (loginSc.nextLine().trim()) {
            case "1" -> "Critical"; case "2" -> "High"; case "3" -> "Medium"; default -> "Low";
        };
        System.out.print("Description: "); loginSc.nextLine();
        System.out.printf("\nFiled: [%s] %s — by %s (Status: Open)%n", sev, title, loggedUser);
        notifQ.enqueue("New Bug", title + " [" + sev + "] by " + loggedUser);
    }

    private static void updateStatus() {
        viewAllBugs();
        System.out.print("\nBug ID to update: ");
        int id; try { id = Integer.parseInt(loginSc.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("Invalid ID."); return; }
        Bug b = htc.get(id);
        if (b == null) { System.out.println("Bug not found."); return; }
        String next = smChain.next(b.status);
        if (next == null) { System.out.println("Already Closed."); return; }
        System.out.println("Current: " + b.status + "  ->  Next: " + next);
        System.out.print("Confirm? (y/n): ");
        if (loginSc.nextLine().trim().equalsIgnoreCase("y")) {
            String prev = b.status; b.status = next;
            audits.get(id).push("Changed by " + loggedUser + ": " + prev + " -> " + next, next);
            notifQ.enqueue("Bug Updated", "BUG-" + id + " -> " + next);
            System.out.println("Updated to: " + next);
        }
    }

    private static void viewAudit() {
        System.out.print("\nBug ID: ");
        int id; try { id = Integer.parseInt(loginSc.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("Invalid ID."); return; }
        AuditStack as = audits.get(id);
        if (as == null) { System.out.println("Bug not found."); return; }
        System.out.println("\n--- Audit for BUG-" + id + " (newest first) ---");
        as.list().forEach(System.out::println);
        System.out.println("Size: " + as.size() + "  Latest: " + as.peek());
    }

    private static void viewTopPriority() {
        System.out.println("\n--- Top 5 Active Bugs (Skip List) ---");
        pq.peekTop(5).forEach(b -> System.out.println("  " + b));
    }

    private static void viewNotifications() {
        System.out.println("\n--- Notifications (Circular Queue) — Unread: " + notifQ.unreadCount() + " ---");
        notifQ.all().forEach(System.out::println);
        notifQ.markAllRead();
    }

    // =========================================================================
    //  MAIN
    // =========================================================================
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("       BUG TRACKER — Issue Registry     ");
        System.out.println("========================================");
        int attempts = 0;
        while (loggedUser == null && attempts < 3) {
            if (doLogin()) break;
            attempts++;
            if (attempts < 3) System.out.println("Try again (" + (3 - attempts) + " left).");
        }
        if (loggedUser == null) { System.out.println("Too many failed attempts. Exiting."); return; }
        mainMenu();
        System.out.println("Goodbye.");
    }
}