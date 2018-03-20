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

import java.util.HashMap;

import rr.meta.SourceLocation;
import rr.state.ShadowThread;
import rr.state.ShadowVar;

public class GoldilocksVarState implements ShadowVar {	

	/** Sync set that applies to last write and all last reads */
	SyncSet univSet;
	
	/** Sync set for last write */
	final SyncSet writeSet;
	
	/** Sync sets for each thread's last read, if any, since last write */
	final HashMap<ShadowThread,SyncSet> readSets;
	
	public GoldilocksVarState(ShadowThread st, GoldilocksThreadState threadElement, boolean isWrite, SourceLocation srcLoc) {
		if (isWrite) {
			univSet = SyncSet.makeSyncSet(threadElement, st.getInnermostLock(), st, srcLoc);
			writeSet = new SyncSet();
			readSets = new HashMap<ShadowThread,SyncSet>();
		} else {
			univSet = null;
			writeSet = new SyncSet();
			readSets = new HashMap<ShadowThread,SyncSet>();
			readSets.put(st, SyncSet.makeSyncSet(threadElement, st.getInnermostLock(), st, srcLoc));
		}
	}

	@Override
	public synchronized String toString() {
		String s = "[univSet: " + univSet + ", " + "writeSet: " + writeSet;
		for (ShadowThread st : readSets.keySet()) {
			SyncSet syncSet = readSets.get(st);
			s += (", readSet for T" + st.getTid() + ": " + syncSet);
		}
		s += "]";
		return s;
	}
}
