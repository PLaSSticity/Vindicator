/******************************************************************************

Copyright (c) 2016, Cormac Flanagan (University of California, Santa Cruz)
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

package tools.goldilocks;

import java.util.Iterator;
import java.util.WeakHashMap;

import acme.util.Assert;
import rr.meta.SourceLocation;
import rr.state.ShadowLock;
import rr.state.ShadowThread;

class SyncSet /*implements Iterable<SyncElement>*/ {

	final WeakHashMap<SyncElement,Provenance> syncElements;
	Transfer precedingTransfer;

	SyncSet() {
		syncElements = new WeakHashMap<SyncElement,Provenance>();
	}
	
	/*
	private SyncSet(SyncSet other) {
		syncElements = new ConcurrentHashMap<SyncElement,Provenance>(other.syncElements);
	}
	*/
	
	boolean hasSyncElement(SyncElement elem) {
		return syncElements.containsKey(elem);
	}

	boolean hasHeldLock(ShadowThread st) {
		for (SyncElement syncElement : syncElements.keySet()) {
			if (syncElement instanceof GoldilocksLockState &&
					((GoldilocksLockState)syncElement).getPeer().getHoldingThread() == st) {
				getProvenanceForSyncElement(syncElement).markUsed();
				return true;
			}
		}
		return false;
	}
	
	Provenance getProvenanceForSyncElement(SyncElement elem) {
		return syncElements.get(elem);
	}

	private void addSyncElement(SyncElement elem, Provenance prov) {
		syncElements.put(elem, prov);
	}
	
	boolean isEmpty() {
		return syncElements.isEmpty();
	}
	
	void clear() {
		syncElements.clear();
		precedingTransfer = Transfer.latestTransfer;
	}

	static SyncSet makeSyncSet(GoldilocksThreadState threadState, ShadowLock innermostLock, ShadowThread st, SourceLocation srcLoc) {
		SyncSet syncSet = null;
		if (threadState != null) {
			syncSet = new SyncSet();
			if (Provenance.trainingMode || (Provenance.threadSourceLocationsFromTraining.contains(srcLoc) || !Provenance.allSourceLocationsFromTraining.contains(srcLoc))) {
				syncSet.syncElements.put(threadState, Provenance.getProvenance(srcLoc, Provenance.THREAD));
				if (GoldilocksTool.COUNT_OPERATIONS) GoldilocksTool.syncSetElementsAddedByAccesses.inc(st.getTid());
			}
		}
		if (innermostLock != null) {
			if (syncSet == null) {
				syncSet = new SyncSet();
			}
			if (Provenance.trainingMode || (Provenance.lockSourceLocationsFromTraining.contains(srcLoc) || !Provenance.allSourceLocationsFromTraining.contains(srcLoc))) {
				syncSet.syncElements.put(GoldilocksTool.getLockState(innermostLock), Provenance.getProvenance(srcLoc, Provenance.LOCK));
				if (GoldilocksTool.COUNT_OPERATIONS) GoldilocksTool.syncSetElementsAddedByAccesses.inc(st.getTid());
			}
		}
		if (syncSet != null) {
			syncSet.precedingTransfer = Transfer.latestTransfer;
		}
		return syncSet;
	}
	
	// TODO: Trying out adding every srcLoc to global set in the following two methods.

	// TODO: not using univSet (yet)
	
	void resetRetainingHeldLocks(GoldilocksThreadState threadState, ShadowLock innermostLock, ShadowThread st, SourceLocation srcLoc) {
		if (Provenance.trainingMode) {
			Provenance.allSourceLocations.put(srcLoc, srcLoc);
		}

		if (threadState != null) {
			Provenance threadProv = this.getProvenanceForSyncElement(threadState);
			if (threadProv == null) {
				if (Provenance.trainingMode || (Provenance.threadSourceLocationsFromTraining.contains(srcLoc) || !Provenance.allSourceLocationsFromTraining.contains(srcLoc))) {
					syncElements.put(threadState, Provenance.getProvenance(srcLoc, Provenance.THREAD));
					if (GoldilocksTool.COUNT_OPERATIONS) GoldilocksTool.syncSetElementsAddedByAccesses.inc(st.getTid());
				}
			}
		}
		if (innermostLock != null) {
			Provenance innermostLockProv = this.getProvenanceForSyncElement(GoldilocksTool.getLockState(innermostLock));
			if (innermostLockProv == null) {
				if (Provenance.trainingMode || (Provenance.lockSourceLocationsFromTraining.contains(srcLoc) || !Provenance.allSourceLocationsFromTraining.contains(srcLoc))) {
					syncElements.put(GoldilocksTool.getLockState(innermostLock), Provenance.getProvenance(srcLoc, Provenance.LOCK));
					if (GoldilocksTool.COUNT_OPERATIONS) GoldilocksTool.syncSetElementsAddedByAccesses.inc(st.getTid());
				}
			}
		}
		for (Iterator<SyncElement> iter = syncElements.keySet().iterator(); iter.hasNext(); ) {
			SyncElement syncElement = iter.next();
			if (syncElement == threadState) {
				continue;
			}
			else if (syncElement instanceof GoldilocksLockState) {
				// Commented-out check seems excessive:
				//if (((GoldilocksLockState)syncElement).getPeer().getHoldingThread() == st) {
				if (((((GoldilocksLockState)syncElement).getPeer() == st.getInnermostLock()))) {
					continue;
				}
			}
			iter.remove();
		}
		precedingTransfer = Transfer.latestTransfer;
	}
	
	boolean performTransfer(GoldilocksThreadState threadState) {
		Transfer t = (precedingTransfer == null ? null : precedingTransfer.getNext());
		while (t != null) {
			precedingTransfer = t; // We know this transfer will have been processed by this sync set
			if (hasSyncElement(t.from) && !hasSyncElement(t.to)) {
				Provenance newProv = Provenance.getProvenance(t.srcLoc, getProvenanceForSyncElement(t.from));
				addSyncElement(t.to, newProv);
				if (GoldilocksTool.COUNT_OPERATIONS) GoldilocksTool.syncSetElementsAddedByLazyTransfer.inc(threadState.getPeer().getTid());
				if (t.to instanceof GoldilocksLockState && ((GoldilocksLockState)t.to).getPeer().getHoldingThread() == threadState.getPeer()) {
					newProv.markUsed();
					return true;
				}
				if (t.to == threadState) {
					newProv.markUsed();
					return true;
				}
			}
			t = t.getNext();
		}
		return false;
	}
	
	@Override
	public boolean equals(Object o) {
		return syncElements.equals(((SyncSet)o).syncElements);
	}
	
	@Override
	public int hashCode() {
		return syncElements.hashCode();
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer("{");
		String delim = "";
		int i = 0;
		for (SyncElement elem : syncElements.keySet()) {
			i++;
			if (i > 5) {
				sb.append(", ...");
				break;
			}
			sb.append(delim + elem);
			delim = ", ";
		}
		sb.append("}");
		return sb.toString();
	}

	/*
	@Override
	public Iterator<SyncElement> iterator() {
		return syncElements.keySet().iterator();
	}
	*/
}
