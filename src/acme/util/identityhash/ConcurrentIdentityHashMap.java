/******************************************************************************

Copyright (c) 2010, Cormac Flanagan (University of California, Santa Cruz)
                    and Stephen Freund (Williams College) 

All rights reserved.  

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

 * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

 * Neither the names of the University of California, Santa Cruz
      and Williams College nor the names of its contributors may be
      used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 ******************************************************************************/

package acme.util.identityhash;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import acme.util.Util;

/**
 * A modified version of the Java library class that uses 
 * Util.identityHashcode.
 */

/**
 * A hash table supporting full concurrency of retrievals and
 * adjustable expected concurrency for updates. This class obeys the
 * same functional specification as {@link java.util.Hashtable}, and
 * includes versions of methods corresponding to each method of
 * <tt>Hashtable</tt>. However, even though all operations are
 * thread-safe, retrieval operations do <em>not</em> entail locking,
 * and there is <em>not</em> any support for locking the entire table
 * in a way that prevents all access.  This class is fully
 * interoperable with <tt>Hashtable</tt> in programs that rely on its
 * thread safety but not on its synchronization details.
 *
 * <p> Retrieval operations (including <tt>get</tt>) generally do not
 * block, so may overlap with update operations (including
 * <tt>put</tt> and <tt>remove</tt>). Retrievals reflect the results
 * of the most recently <em>completed</em> update operations holding
 * upon their onset.  For aggregate operations such as <tt>putAll</tt>
 * and <tt>clear</tt>, concurrent retrievals may reflect insertion or
 * removal of only some entries.  Similarly, Iterators and
 * Enumerations return elements reflecting the state of the hash table
 * at some point at or since the creation of the iterator/enumeration.
 * They do <em>not</em> throw
 * {@link ConcurrentModificationException}.  However, iterators are
 * designed to be used by only one thread at a time.
 *
 * <p> The allowed concurrency among update operations is guided by
 * the optional <tt>concurrencyLevel</tt> constructor argument
 * (default 16), which is used as a hint for internal sizing.  The
 * table is internally partitioned to try to permit the indicated
 * number of concurrent updates without contention. Because placement
 * in hash tables is essentially random, the actual concurrency will
 * vary.  Ideally, you should choose a value to accommodate as many
 * threads as will ever concurrently modify the table. Using a
 * significantly higher value than you need can waste space and time,
 * and a significantly lower value can lead to thread contention. But
 * overestimates and underestimates within an order of magnitude do
 * not usually have much noticeable impact. A value of one is
 * appropriate when it is known that only one thread will modify and
 * all others will only read. Also, resizing this or any other kind of
 * hash table is a relatively slow operation, so, when possible, it is
 * a good idea to provide estimates of expected table sizes in
 * constructors.
 *
 * <p>This class and its views and iterators implement all of the
 * <em>optional</em> methods of the {@link Map} and {@link Iterator}
 * interfaces.
 *
 * <p> Like {@link java.util.Hashtable} but unlike {@link
 * java.util.HashMap}, this class does NOT allow <tt>null</tt> to be
 * used as a key or value.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../guide/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values 
 */
public class ConcurrentIdentityHashMap<K, V> extends AbstractMap<K, V> {

	/*
	 * The basic strategy is to subdivide the table among Segments,
	 * each of which itself is a concurrently readable hash table.
	 */

	/* ---------------- Constants -------------- */

	/**
	 * The default initial number of table slots for this table.
	 * Used when not otherwise specified in constructor.
	 */
	static int DEFAULT_INITIAL_CAPACITY = 16;

	/**
	 * The maximum capacity, used if a higher value is implicitly
	 * specified by either of the constructors with arguments.  MUST
	 * be a power of two <= 1<<30 to ensure that entries are indexible
	 * using ints.
	 */
	static final int MAXIMUM_CAPACITY = 1 << 30; 

	/**
	 * The default load factor for this table.  Used when not
	 * otherwise specified in constructor.
	 */
	static final float DEFAULT_LOAD_FACTOR = 0.75f;

	/**
	 * The default number of concurrency control segments.
	 **/
	static final int DEFAULT_SEGMENTS = 16;

	/**
	 * The maximum number of segments to allow; used to bound
	 * constructor arguments.
	 */
	static final int MAX_SEGMENTS = 1 << 16; // slightly conservative

	/**
	 * Number of unsynchronized retries in size and containsValue
	 * methods before resorting to locking. This is used to avoid
	 * unbounded retries if tables undergo continuous modification
	 * which would make it impossible to obtain an accurate result.
	 */
	static final int RETRIES_BEFORE_LOCK = 2;

	/* ---------------- Fields -------------- */

	/**
	 * Mask value for indexing into segments. The upper bits of a
	 * key's hash code are used to choose the segment.
	 **/
	final int segmentMask;

	/**
	 * Shift value for indexing within segments.
	 **/
	final int segmentShift;

	/**
	 * The segments, each of which is a specialized hash table
	 */
	final Segment[] segments;

	transient Set<K> keySet;
	transient Set<Map.Entry<K,V>> entrySet;
	transient Collection<V> values;

	/* ---------------- Small Utilities -------------- */

	/**
	 * Returns a hash code for non-null Object x.
	 * Uses the same hash code spreader as most other java.util hash tables.
	 * @param x the object serving as a key
	 * @return the hash code
	 */
	static int hash(Object x) {
		int h = Util.identityHashCode(x);
		return h;
	}

	/**
	 * Returns the segment that should be used for key with given hash
	 * @param hash the hash code for the key
	 * @return the segment
	 */
	final Segment<K,V> segmentFor(int hash) {
		return segments[(hash >>> segmentShift) & segmentMask];
	}

	/* ---------------- Inner Classes -------------- */

	/**
	 * ConcurrentHashMap list entry. Note that this is never exported
	 * out as a user-visible Map.Entry. 
	 * 
	 * Because the value field is volatile, not final, it is legal wrt
	 * the Java Memory Model for an unsynchronized reader to see null
	 * instead of initial value when read via a data race.  Although a
	 * reordering leading to this is not likely to ever actually
	 * occur, the Segment.readValueUnderLock method is used as a
	 * backup in case a null (pre-initialized) value is ever seen in
	 * an unsynchronized access method.
	 */
	static final class HashEntry<K,V> {
		final K key;
		final int hash;
		volatile V value;
		final HashEntry<K,V> next;

		HashEntry(K key, int hash, HashEntry<K,V> next, V value) {
			this.key = key;
			this.hash = hash;
			this.next = next;
			this.value = value;
		}
	}

	/**
	 * Segments are specialized versions of hash tables.  This
	 * subclasses from ReentrantLock opportunistically, just to
	 * simplify some locking and avoid separate construction.
	 **/
	static final class Segment<K,V> extends ReentrantLock implements Serializable {
		/*
		 * Segments maintain a table of entry lists that are ALWAYS
		 * kept in a consistent state, so can be read without locking.
		 * Next fields of nodes are immutable (final).  All list
		 * additions are performed at the front of each bin. This
		 * makes it easy to check changes, and also fast to traverse.
		 * When nodes would otherwise be changed, new nodes are
		 * created to replace them. This works well for hash tables
		 * since the bin lists tend to be short. (The average length
		 * is less than two for the default load factor threshold.)
		 *
		 * Read operations can thus proceed without locking, but rely
		 * on selected uses of volatiles to ensure that completed
		 * write operations performed by other threads are
		 * noticed. For most purposes, the "count" field, tracking the
		 * number of elements, serves as that volatile variable
		 * ensuring visibility.  This is convenient because this field
		 * needs to be read in many read operations anyway:
		 *
		 *   - All (unsynchronized) read operations must first read the
		 *     "count" field, and should not look at table entries if
		 *     it is 0.
		 *
		 *   - All (synchronized) write operations should write to
		 *     the "count" field after structurally changing any bin.
		 *     The operations must not take any action that could even
		 *     momentarily cause a concurrent read operation to see
		 *     inconsistent data. This is made easier by the nature of
		 *     the read operations in Map. For example, no operation
		 *     can reveal that the table has grown but the threshold
		 *     has not yet been updated, so there are no atomicity
		 *     requirements for this with respect to reads.
		 *
		 * As a guide, all critical volatile reads and writes to the
		 * count field are marked in code comments.
		 */

		private static final long serialVersionUID = 2249069246763182397L;

		/**
		 * The number of elements in this segment's region.
		 **/
		transient volatile int count;

		/**
		 * Number of updates that alter the size of the table. This is
		 * used during bulk-read methods to make sure they see a
		 * consistent snapshot: If modCounts change during a traversal
		 * of segments computing size or checking containsValue, then
		 * we might have an inconsistent view of state so (usually)
		 * must retry.
		 */
		transient int modCount;

		/**
		 * The table is rehashed when its size exceeds this threshold.
		 * (The value of this field is always (int)(capacity *
		 * loadFactor).)
		 */
		transient int threshold;

		/**
		 * The per-segment table. Declared as a raw type, casted
		 * to HashEntry<K,V> on each use.
		 */
		transient volatile HashEntry[] table;

		/**
		 * The load factor for the hash table.  Even though this value
		 * is same for all segments, it is replicated to avoid needing
		 * links to outer object.
		 * @serial
		 */
		final float loadFactor;

		Segment(int initialCapacity, float lf) {
			loadFactor = lf;
			setTable(new HashEntry[initialCapacity]);
		}

		/**
		 * Set table to new HashEntry target.
		 * Call only while holding targe or in constructor.
		 **/
		void setTable(HashEntry[] newTable) {
			threshold = (int)(newTable.length * loadFactor);
			table = newTable;
		}

		/**
		 * Return properly casted first entry of bin for given hash
		 */
		HashEntry<K,V> getFirst(int hash) {
			HashEntry[] tab = table;
			return tab[hash & (tab.length - 1)];
		}

		/**
		 * Read value field of an entry under targe. Called if value
		 * field ever appears to be null. This is possible only if a
		 * compiler happens to reorder a HashEntry initialization with
		 * its table assignment, which is legal under memory model
		 * but is not known to ever occur.
		 */
		V readValueUnderLock(HashEntry<K,V> e) {
			lock();
			try {
				return e.value;
			} finally {
				unlock();
			}
		}

		/* Specialized implementations of map methods */

		V get(Object key, int hash) {
			if (count != 0) { // read-volatile
				HashEntry<K,V> e = getFirst(hash);
				while (e != null) {
					if (e.hash == hash && key == e.key) {
						V v = e.value;
						if (v != null)
							return v;
						return readValueUnderLock(e); // recheck
					}
					e = e.next;
				}
			}
			return null;
		}

		boolean containsKey(Object key, int hash) {
			if (count != 0) { // read-volatile
				HashEntry<K,V> e = getFirst(hash);
				while (e != null) {
					if (e.hash == hash && key == e.key)
						return true;
					e = e.next;
				}
			}
			return false;
		}

		boolean containsValue(Object value) {
			if (count != 0) { // read-volatile
				HashEntry[] tab = table;
				int len = tab.length;
				for (int i = 0 ; i < len; i++) {
					for (HashEntry<K,V> e = tab[i]; 
					e != null ; 
					e = e.next) {
						V v = e.value;
						if (v == null) // recheck
							v = readValueUnderLock(e);
						if (value.equals(v))
							return true;
					}
				}
			}
			return false;
		}

		boolean replace(K key, int hash, V oldValue, V newValue) {
			lock();
			try {
				HashEntry<K,V> e = getFirst(hash);
				while (e != null && (e.hash != hash || key != e.key))
					e = e.next;

				boolean replaced = false;
				if (e != null && oldValue.equals(e.value)) {
					replaced = true;
					e.value = newValue;
				}
				return replaced;
			} finally {
				unlock();
			}
		}

		V replace(K key, int hash, V newValue) {
			lock();
			try {
				HashEntry<K,V> e = getFirst(hash);
				while (e != null && (e.hash != hash || key != e.key))
					e = e.next;

				V oldValue = null;
				if (e != null) {
					oldValue = e.value;
					e.value = newValue;
				}
				return oldValue;
			} finally {
				unlock();
			}
		}


		V put(K key, int hash, V value, boolean onlyIfAbsent) {
			lock();
			try {
				int c = count;
				if (c++ > threshold) {// ensure capacity
					rehash();
				}
				HashEntry[] tab = table;
				int index = hash & (tab.length - 1);
				HashEntry<K,V> first = tab[index];
				HashEntry<K,V> e = first;
				while (e != null && (e.hash != hash || key != e.key))
					e = e.next;

				V oldValue;
				if (e != null) {
					oldValue = e.value;
					if (!onlyIfAbsent)
						e.value = value;
				}
				else {
					oldValue = null;
					++modCount;
					tab[index] = new HashEntry<K,V>(key, hash, first, value);
					count = c; // write-volatile
				}
				return oldValue;
			} finally {
				unlock();
			}
		}

		void rehash() {
			HashEntry[] oldTable = table;            
			int oldCapacity = oldTable.length;
			if (oldCapacity >= MAXIMUM_CAPACITY)
				return;

			/*
			 * Reclassify nodes in each list to new Map.  Because we are
			 * using power-of-two expansion, the elements from each bin
			 * must either stay at same index, or move with a power of two
			 * offset. We eliminate unnecessary node creation by catching
			 * cases where old nodes can be reused because their next
			 * fields won't change. Statistically, at the default
			 * threshold, only about one-sixth of them need cloning when
			 * a table doubles. The nodes they replace will be garbage
			 * collectable as soon as they are no longer referenced by any
			 * reader thread that may be in the midst of traversing table
			 * right now.
			 */

			HashEntry[] newTable = new HashEntry[oldCapacity << 1];
			threshold = (int)(newTable.length * loadFactor);
			int sizeMask = newTable.length - 1;
			for (int i = 0; i < oldCapacity ; i++) {
				// We need to guarantee that any existing reads of old Map can
				//  proceed. So we cannot yet null out each bin.
				HashEntry<K,V> e = oldTable[i];

				if (e != null) {
					HashEntry<K,V> next = e.next;
					int idx = e.hash & sizeMask;

					//  Single node on list
					if (next == null)
						newTable[idx] = e;

					else {
						// Reuse trailing consecutive sequence at same slot
						HashEntry<K,V> lastRun = e;
						int lastIdx = idx;
						for (HashEntry<K,V> last = next;
						last != null;
						last = last.next) {
							int k = last.hash & sizeMask;
							if (k != lastIdx) {
								lastIdx = k;
								lastRun = last;
							}
						}
						newTable[lastIdx] = lastRun;

						// Clone all remaining nodes
						for (HashEntry<K,V> p = e; p != lastRun; p = p.next) {
							int k = p.hash & sizeMask;
							HashEntry<K,V> n = newTable[k];
							newTable[k] = new HashEntry<K,V>(p.key, p.hash,
									n, p.value);
						}
					}
				}
			}
			table = newTable;
		}

		/**
		 * Remove; match on key only if value null, else match both.
		 */
		V remove(Object key, int hash, Object value) {
			lock();
			try {
				int c = count - 1;
				HashEntry[] tab = table;
				int index = hash & (tab.length - 1);
				HashEntry<K,V> first = tab[index];
				HashEntry<K,V> e = first;
				while (e != null && (e.hash != hash || key != e.key))
					e = e.next;

				V oldValue = null;
				if (e != null) {
					V v = e.value;
					if (value == null || value.equals(v)) {
						oldValue = v;
						// All entries following removed node can stay
						// in list, but all preceding ones need to be
						// cloned.
						++modCount;
						HashEntry<K,V> newFirst = e.next;
						for (HashEntry<K,V> p = first; p != e; p = p.next)
							newFirst = new HashEntry<K,V>(p.key, p.hash,  
									newFirst, p.value);
						tab[index] = newFirst;
						count = c; // write-volatile
					}
				}
				return oldValue;
			} finally {
				unlock();
			}
		}

		void clear() {
			if (count != 0) {
				lock();
				try {
					HashEntry[] tab = table;
					for (int i = 0; i < tab.length ; i++)
						tab[i] = null;
					++modCount;
					count = 0; // write-volatile
				} finally {
					unlock();
				}
			}
		}
	}



	/* ---------------- Public operations -------------- */

	/**
	 * Creates a new, empty map with the specified initial
	 * capacity, load factor, and concurrency level.
	 *
	 * @param initialCapacity the initial capacity. The implementation
	 * performs internal sizing to accommodate this many elements.
	 * @param loadFactor  the load factor threshold, used to control resizing.
	 * Resizing may be performed when the average number of elements per
	 * bin exceeds this threshold.
	 * @param concurrencyLevel the estimated number of concurrently
	 * updating threads. The implementation performs internal sizing
	 * to try to accommodate this many threads.  
	 * @throws IllegalArgumentException if the initial capacity is
	 * negative or the load factor or concurrencyLevel are
	 * nonpositive.
	 */
	public ConcurrentIdentityHashMap(int initialCapacity,
			float loadFactor, int concurrencyLevel) {
		if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)
			throw new IllegalArgumentException();

		if (concurrencyLevel > MAX_SEGMENTS)
			concurrencyLevel = MAX_SEGMENTS;

		// Find power-of-two sizes best matching arguments
		int sshift = 0;

		int ssize = 1;
		while (ssize < concurrencyLevel) {
			++sshift;
			ssize <<= 1;
		}
		segmentShift = 32 - sshift;
		segmentMask = ssize - 1;
		this.segments = new Segment[ssize];

		if (initialCapacity > MAXIMUM_CAPACITY)
			initialCapacity = MAXIMUM_CAPACITY;
		int c = initialCapacity / ssize;
		if (c * ssize < initialCapacity)
			++c;
		int cap = 1;
		while (cap < c)
			cap <<= 1;

		for (int i = 0; i < this.segments.length; ++i)
			this.segments[i] = new Segment<K,V>(cap, loadFactor);
	}

	/**
	 * Creates a new, empty map with the specified initial
	 * capacity, and with default load factor and concurrencyLevel.
	 *
	 * @param initialCapacity the initial capacity. The implementation
	 * performs internal sizing to accommodate this many elements.
	 * @throws IllegalArgumentException if the initial capacity of
	 * elements is negative.
	 */
	public ConcurrentIdentityHashMap(int initialCapacity) {
		this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
	}

	/**
	 * Creates a new, empty map with a default initial capacity,
	 * load factor, and concurrencyLevel.
	 */
	public ConcurrentIdentityHashMap() {
		this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
	}

	/**
	 * Creates a new map with the same mappings as the given map.  The
	 * map is created with a capacity of twice the number of mappings in
	 * the given map or 11 (whichever is greater), and a default load factor
	 * and concurrencyLevel.
	 * @param t the map
	 */
	public ConcurrentIdentityHashMap(Map<? extends K, ? extends V> t) {
		this(Math.max((int) (t.size() / DEFAULT_LOAD_FACTOR) + 1,
				11),
				DEFAULT_LOAD_FACTOR, DEFAULT_SEGMENTS);
		putAll(t);
	}

	// inherit Map javadoc
	@Override
	public boolean isEmpty() {
		final Segment[] segments = this.segments;
		/*
		 * We keep track of per-segment modCounts to avoid ABA
		 * problems in which an element in one segment was added and
		 * in another removed during traversal, in which case the
		 * table was never actually empty at any point. Note the
		 * similar use of modCounts in the size() and containsValue()
		 * methods, which are the only other methods also susceptible
		 * to ABA problems.
		 */
		int[] mc = new int[segments.length];
		int mcsum = 0;
		for (int i = 0; i < segments.length; ++i) {
			if (segments[i].count != 0)
				return false;
			else 
				mcsum += mc[i] = segments[i].modCount;
		}
		// If mcsum happens to be zero, then we know we got a snapshot
		// before any modifications at all were made.  This is
		// probably common enough to bother tracking.
		if (mcsum != 0) {
			for (int i = 0; i < segments.length; ++i) {
				if (segments[i].count != 0 ||
						mc[i] != segments[i].modCount) 
					return false;
			}
		}
		return true;
	}

	// inherit Map javadoc
	@Override
	public int size() {
		final Segment[] segments = this.segments;
		long sum = 0;
		long check = 0;
		int[] mc = new int[segments.length];
		// Try a few times to get accurate count. On failure due to
		// continuous async changes in table, resort to locking.
		for (int k = 0; k < RETRIES_BEFORE_LOCK; ++k) {
			check = 0;
			sum = 0;
			int mcsum = 0;
			for (int i = 0; i < segments.length; ++i) {
				sum += segments[i].count;
				mcsum += mc[i] = segments[i].modCount;
			}
			if (mcsum != 0) {
				for (int i = 0; i < segments.length; ++i) {
					check += segments[i].count;
					if (mc[i] != segments[i].modCount) {
						check = -1; // force retry
						break;
					}
				}
			}
			if (check == sum) 
				break;
		}
		if (check != sum) { // Resort to locking all segments
			sum = 0;
			for (int i = 0; i < segments.length; ++i) 
				segments[i].lock();
			for (int i = 0; i < segments.length; ++i) 
				sum += segments[i].count;
			for (int i = 0; i < segments.length; ++i) 
				segments[i].unlock();
		}
		if (sum > Integer.MAX_VALUE)
			return Integer.MAX_VALUE;
		else
			return (int)sum;
	}


	/**
	 * Returns the value to which the specified key is mapped in this table.
	 *
	 * @param   key   a key in the table.
	 * @return  the value to which the key is mapped in this table;
	 *          <tt>null</tt> if the key is not mapped to any value in
	 *          this table.
	 * @throws  NullPointerException  if the key is
	 *               <tt>null</tt>.
	 */
	@Override
	public V get(Object key) {
		int hash = hash(key); // throws NullPointerException if key null
		return segmentFor(hash).get(key, hash);
	}

	public V get(Object key, int hash) {
		return segmentFor(hash).get(key, hash);
	}


	/**
	 * Tests if the specified object is a key in this table.
	 *
	 * @param   key   possible key.
	 * @return  <tt>true</tt> if and only if the specified object
	 *          is a key in this table, as determined by the
	 *          <tt>equals</tt> method; <tt>false</tt> otherwise.
	 * @throws  NullPointerException  if the key is
	 *               <tt>null</tt>.
	 */
	@Override
	public boolean containsKey(Object key) {
		int hash = hash(key); // throws NullPointerException if key null
		return segmentFor(hash).containsKey(key, hash);
	}

	/**
	 * Returns <tt>true</tt> if this map maps one or more keys to the
	 * specified value. Note: This method requires a full internal
	 * traversal of the hash table, and so is much slower than
	 * method <tt>containsKey</tt>.
	 *
	 * @param value value whose presence in this map is to be tested.
	 * @return <tt>true</tt> if this map maps one or more keys to the
	 * specified value.
	 * @throws  NullPointerException  if the value is <tt>null</tt>.
	 */
	@Override
	public boolean containsValue(Object value) {
		if (value == null)
			throw new NullPointerException();

		// See explanation of modCount use above

		final Segment[] segments = this.segments;
		int[] mc = new int[segments.length];

		// Try a few times without locking
		for (int k = 0; k < RETRIES_BEFORE_LOCK; ++k) {
			int mcsum = 0;
			for (int i = 0; i < segments.length; ++i) {
				mcsum += mc[i] = segments[i].modCount;
				if (segments[i].containsValue(value))
					return true;
			}
			boolean cleanSweep = true;
			if (mcsum != 0) {
				for (int i = 0; i < segments.length; ++i) {
					if (mc[i] != segments[i].modCount) {
						cleanSweep = false;
						break;
					}
				}
			}
			if (cleanSweep)
				return false;
		}
		// Resort to locking all segments
		for (int i = 0; i < segments.length; ++i) 
			segments[i].lock();
		boolean found = false;
		try {
			for (int i = 0; i < segments.length; ++i) {
				if (segments[i].containsValue(value)) {
					found = true;
					break;
				}
			}
		} finally {
			for (int i = 0; i < segments.length; ++i) 
				segments[i].unlock();
		}
		return found;
	}

	/**
	 * Legacy method testing if some key maps into the specified value
	 * in this table.  This method is identical in functionality to
	 * {@link #containsValue}, and  exists solely to ensure
	 * full compatibility with class {@link java.util.Hashtable},
	 * which supported this method prior to introduction of the
	 * Java Collections framework.

	 * @param      value   a value to search for.
	 * @return     <tt>true</tt> if and only if some key maps to the
	 *             <tt>value</tt> argument in this table as
	 *             determined by the <tt>equals</tt> method;
	 *             <tt>false</tt> otherwise.
	 * @throws  NullPointerException  if the value is <tt>null</tt>.
	 */
	public boolean contains(Object value) {
		return containsValue(value);
	}

	/**
	 * Maps the specified <tt>key</tt> to the specified
	 * <tt>value</tt> in this table. Neither the key nor the
	 * value can be <tt>null</tt>. 
	 *
	 * <p> The value can be retrieved by calling the <tt>get</tt> method
	 * with a key that is equal to the original key.
	 *
	 * @param      key     the table key.
	 * @param      value   the value.
	 * @return     the previous value of the specified key in this table,
	 *             or <tt>null</tt> if it did not have one.
	 * @throws  NullPointerException  if the key or value is
	 *               <tt>null</tt>.
	 */
	@Override
	public V put(K key, V value) {
		if (value == null)
			throw new NullPointerException();
		int hash = hash(key);
		return segmentFor(hash).put(key, hash, value, false);
	}

	/**
	 * If the specified key is not already associated
	 * with a value, associate it with the given value.
	 * This is equivalent to
	 * <pre>
	 *   if (!map.containsKey(key)) 
	 *      return map.put(key, value);
	 *   else
	 *      return map.get(key);
	 * </pre>
	 * Except that the action is performed atomically.
	 * @param key key with which the specified value is to be associated.
	 * @param value value to be associated with the specified key.
	 * @return previous value associated with specified key, or <tt>null</tt>
	 *         if there was no mapping for key.
	 * @throws NullPointerException if the specified key or value is
	 *            <tt>null</tt>.
	 */
	public V putIfAbsent(K key, V value) {
		if (value == null)
			throw new NullPointerException();
		int hash = hash(key);
		return segmentFor(hash).put(key, hash, value, true);
	}

	public V putIfAbsent(K key, V value, int hash) {
		if (value == null)
			throw new NullPointerException();
		return segmentFor(hash).put(key, hash, value, true);
	}


	/**
	 * Copies all of the mappings from the specified map to this one.
	 *
	 * These mappings replace any mappings that this map had for any of the
	 * keys currently in the specified Map.
	 *
	 * @param t Mappings to be stored in this map.
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> t) {
		for (Iterator<? extends Map.Entry<? extends K, ? extends V>> it = t.entrySet().iterator(); it.hasNext(); ) {
			Entry<? extends K, ? extends V> e = it.next();
			put(e.getKey(), e.getValue());
		}
	}

	/**
	 * Removes the key (and its corresponding value) from this
	 * table. This method does nothing if the key is not in the table.
	 *
	 * @param   key   the key that needs to be removed.
	 * @return  the value to which the key had been mapped in this table,
	 *          or <tt>null</tt> if the key did not have a mapping.
	 * @throws  NullPointerException  if the key is
	 *               <tt>null</tt>.
	 */
	@Override
	public V remove(Object key) {
		int hash = hash(key);
		return segmentFor(hash).remove(key, hash, null);
	}

	/**
	 * Remove entry for key only if currently mapped to given value.
	 * Acts as
	 * <pre> 
	 *  if (map.get(key).equals(value)) {
	 *     map.remove(key);
	 *     return true;
	 * } else return false;
	 * </pre>
	 * except that the action is performed atomically.
	 * @param key key with which the specified value is associated.
	 * @param value value associated with the specified key.
	 * @return true if the value was removed
	 * @throws NullPointerException if the specified key is
	 *            <tt>null</tt>.
	 */
	public boolean remove(Object key, Object value) {
		int hash = hash(key);
		return segmentFor(hash).remove(key, hash, value) != null;
	}


	/**
	 * Replace entry for key only if currently mapped to given value.
	 * Acts as
	 * <pre> 
	 *  if (map.get(key).equals(oldValue)) {
	 *     map.put(key, newValue);
	 *     return true;
	 * } else return false;
	 * </pre>
	 * except that the action is performed atomically.
	 * @param key key with which the specified value is associated.
	 * @param oldValue value expected to be associated with the specified key.
	 * @param newValue value to be associated with the specified key.
	 * @return true if the value was replaced
	 * @throws NullPointerException if the specified key or values are
	 * <tt>null</tt>.
	 */
	public boolean replace(K key, V oldValue, V newValue) {
		if (oldValue == null || newValue == null)
			throw new NullPointerException();
		int hash = hash(key);
		return segmentFor(hash).replace(key, hash, oldValue, newValue);
	}

	/**
	 * Replace entry for key only if currently mapped to some value.
	 * Acts as
	 * <pre> 
	 *  if ((map.containsKey(key)) {
	 *     return map.put(key, value);
	 * } else return null;
	 * </pre>
	 * except that the action is performed atomically.
	 * @param key key with which the specified value is associated.
	 * @param value value to be associated with the specified key.
	 * @return previous value associated with specified key, or <tt>null</tt>
	 *         if there was no mapping for key.  
	 * @throws NullPointerException if the specified key or value is
	 *            <tt>null</tt>.
	 */
	public V replace(K key, V value) {
		if (value == null)
			throw new NullPointerException();
		int hash = hash(key);
		return segmentFor(hash).replace(key, hash, value);
	}


	/**
	 * Removes all mappings from this map.
	 */
	@Override
	public void clear() {
		for (int i = 0; i < segments.length; ++i)
			segments[i].clear();
	}

	/**
	 * Returns a set view of the keys contained in this map.  The set is
	 * backed by the map, so changes to the map are reflected in the set, and
	 * vice-versa.  The set supports element removal, which removes the
	 * corresponding mapping from this map, via the <tt>Iterator.remove</tt>,
	 * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt>, and
	 * <tt>clear</tt> operations.  It does not support the <tt>add</tt> or
	 * <tt>addAll</tt> operations.
	 * The view's returned <tt>iterator</tt> is a "weakly consistent" iterator that
	 * will never throw {@link java.util.ConcurrentModificationException},
	 * and guarantees to traverse elements as they existed upon
	 * construction of the iterator, and may (but is not guaranteed to)
	 * reflect any modifications subsequent to construction.
	 *
	 * @return a set view of the keys contained in this map.
	 */
	@Override
	public Set<K> keySet() {
		Set<K> ks = keySet;
		return (ks != null) ? ks : (keySet = new KeySet());
	}


	/**
	 * Returns a collection view of the values contained in this map.  The
	 * collection is backed by the map, so changes to the map are reflected in
	 * the collection, and vice-versa.  The collection supports element
	 * removal, which removes the corresponding mapping from this map, via the
	 * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
	 * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
	 * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
	 * The view's returned <tt>iterator</tt> is a "weakly consistent" iterator that
	 * will never throw {@link java.util.ConcurrentModificationException},
	 * and guarantees to traverse elements as they existed upon
	 * construction of the iterator, and may (but is not guaranteed to)
	 * reflect any modifications subsequent to construction.
	 *
	 * @return a collection view of the values contained in this map.
	 */
	@Override
	public Collection<V> values() {
		Collection<V> vs = values;
		return (vs != null) ? vs : (values = new Values());
	}


	/**
	 * Returns a collection view of the mappings contained in this map.  Each
	 * element in the returned collection is a <tt>Map.Entry</tt>.  The
	 * collection is backed by the map, so changes to the map are reflected in
	 * the collection, and vice-versa.  The collection supports element
	 * removal, which removes the corresponding mapping from the map, via the
	 * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
	 * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
	 * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
	 * The view's returned <tt>iterator</tt> is a "weakly consistent" iterator that
	 * will never throw {@link java.util.ConcurrentModificationException},
	 * and guarantees to traverse elements as they existed upon
	 * construction of the iterator, and may (but is not guaranteed to)
	 * reflect any modifications subsequent to construction.
	 *
	 * @return a collection view of the mappings contained in this map.
	 */
	@Override
	public Set<Map.Entry<K,V>> entrySet() {
		Set<Map.Entry<K,V>> es = entrySet;
		return (es != null) ? es : (entrySet = new EntrySet());
	}


	/**
	 * Returns an enumeration of the keys in this table.
	 *
	 * @return  an enumeration of the keys in this table.
	 * @see     #keySet
	 */
	public Iterator<K> keys() {
		return new KeyIterator();
	}

	/**
	 * Returns an enumeration of the values in this table.
	 *
	 * @return  an enumeration of the values in this table.
	 * @see     #values
	 */
	public Iterator<V> elements() {
		return new ValueIterator();
	}

	/* ---------------- Iterator Support -------------- */

	abstract class HashIterator {
		int nextSegmentIndex;
		int nextTableIndex;
		HashEntry[] currentTable;
		HashEntry<K, V> nextEntry;
		HashEntry<K, V> lastReturned;

		HashIterator() {
			nextSegmentIndex = segments.length - 1;
			nextTableIndex = -1;
			advance();
		}

		public boolean hasMoreElements() { return hasNext(); }

		final void advance() {
			if (nextEntry != null && (nextEntry = nextEntry.next) != null)
				return;

			while (nextTableIndex >= 0) {
				if ( (nextEntry = currentTable[nextTableIndex--]) != null)
					return;
			}

			while (nextSegmentIndex >= 0) {
				Segment<K,V> seg = segments[nextSegmentIndex--];
				if (seg.count != 0) {
					currentTable = seg.table;
					for (int j = currentTable.length - 1; j >= 0; --j) {
						if ( (nextEntry = currentTable[j]) != null) {
							nextTableIndex = j - 1;
							return;
						}
					}
				}
			}
		}

		public boolean hasNext() { return nextEntry != null; }

		HashEntry<K,V> nextEntry() {
			if (nextEntry == null)
				throw new NoSuchElementException();
			lastReturned = nextEntry;
			advance();
			return lastReturned;
		}

		public void remove() {
			if (lastReturned == null)
				throw new IllegalStateException();
			ConcurrentIdentityHashMap.this.remove(lastReturned.key);
			lastReturned = null;
		}
	}

	final class KeyIterator extends HashIterator implements Iterator<K> {
		public K next() { return super.nextEntry().key; }
		public K nextElement() { return super.nextEntry().key; }
	}

	final class ValueIterator extends HashIterator implements Iterator<V> {
		public V next() { return super.nextEntry().value; }
		public V nextElement() { return super.nextEntry().value; }
	}



	/**
	 * Entry iterator. Exported Entry objects must write-through
	 * changes in setValue, even if the nodes have been cloned. So we
	 * cannot return internal HashEntry objects. Instead, the iterator
	 * itself acts as a forwarding pseudo-entry.
	 */
	final class EntryIterator extends HashIterator implements Map.Entry<K,V>, Iterator<Entry<K,V>> {
		public Map.Entry<K,V> next() {
			nextEntry();
			return this;
		}

		public K getKey() {
			if (lastReturned == null)
				throw new IllegalStateException("Entry was removed");
			return lastReturned.key;
		}

		public V getValue() {
			if (lastReturned == null)
				throw new IllegalStateException("Entry was removed");
			return ConcurrentIdentityHashMap.this.get(lastReturned.key);
		}

		public V setValue(V value) {
			if (lastReturned == null)
				throw new IllegalStateException("Entry was removed");
			return ConcurrentIdentityHashMap.this.put(lastReturned.key, value);
		}

		@Override
		public boolean equals(Object o) {
			// If not acting as entry, just use default.
			if (lastReturned == null)
				return super.equals(o);
			if (!(o instanceof Map.Entry))
				return false;
			Map.Entry e = (Map.Entry)o;
			return eq(getKey(), e.getKey()) && eq(getValue(), e.getValue());
		}

		@Override
		public int hashCode() {
			// If not acting as entry, just use default.
			if (lastReturned == null)
				return super.hashCode();

			Object k = getKey();
			Object v = getValue();
			return ((k == null) ? 0 : Util.identityHashCode(k)) ^
			((v == null) ? 0 : Util.identityHashCode(v));
		}

		@Override
		public String toString() {
			// If not acting as entry, just use default.
			if (lastReturned == null)
				return super.toString();
			else
				return getKey() + "=" + getValue();
		}

		boolean eq(Object o1, Object o2) {
			return (o1 == null ? o2 == null : o1.equals(o2));
		}

	}

	final class KeySet extends AbstractSet<K> {
		@Override
		public Iterator<K> iterator() {
			return new KeyIterator();
		}
		@Override
		public int size() {
			return ConcurrentIdentityHashMap.this.size();
		}
		@Override
		public boolean contains(Object o) {
			return ConcurrentIdentityHashMap.this.containsKey(o);
		}
		@Override
		public boolean remove(Object o) {
			return ConcurrentIdentityHashMap.this.remove(o) != null;
		}
		@Override
		public void clear() {
			ConcurrentIdentityHashMap.this.clear();
		}
	}

	final class Values extends AbstractCollection<V> {
		@Override
		public Iterator<V> iterator() {
			return new ValueIterator();
		}
		@Override
		public int size() {
			return ConcurrentIdentityHashMap.this.size();
		}
		@Override
		public boolean contains(Object o) {
			return ConcurrentIdentityHashMap.this.containsValue(o);
		}
		@Override
		public void clear() {
			ConcurrentIdentityHashMap.this.clear();
		}
	}

	final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
		@Override
		public Iterator<Map.Entry<K,V>> iterator() {
			return new EntryIterator();
		}
		@Override
		public int size() {
			return ConcurrentIdentityHashMap.this.size();
		}
		@Override
		public void clear() {
			ConcurrentIdentityHashMap.this.clear();
		}
	}

	/**
	 * This duplicates java.util.AbstractMap.SimpleEntry until this class
	 * is made accessible.
	 */
	static final class SimpleEntry<K,V> implements Entry<K,V> {
		K key;
		V value;

		public SimpleEntry(K key, V value) {
			this.key   = key;
			this.value = value;
		}

		public SimpleEntry(Entry<K,V> e) {
			this.key   = e.getKey();
			this.value = e.getValue();
		}

		public K getKey() {
			return key;
		}

		public V getValue() {
			return value;
		}

		public V setValue(V value) {
			V oldValue = this.value;
			this.value = value;
			return oldValue;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Map.Entry))
				return false;
			Map.Entry e = (Map.Entry)o;
			return eq(key, e.getKey()) && eq(value, e.getValue());
		}

		@Override
		public int hashCode() {
			return ((key == null) ? 0 : Util.identityHashCode(key)) ^
			((value == null) ? 0 : Util.identityHashCode(value));
		}

		@Override
		public String toString() {
			return key + "=" + value;
		}

		static boolean eq(Object o1, Object o2) {
			return (o1 == null ? o2 == null : o1.equals(o2));
		}
	}
}
