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

package tools.wdc;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.WeakHashMap;

import acme.util.identityhash.WeakIdentityHashMap;
import acme.util.identityhash.WeakIdentityHashSet;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.state.ShadowVar;

public class WDCLockData {

	public final ShadowLock peer;
	public final CV hb;
	public final CV wcp;
	public final CV wdc;
	public HashSet<ShadowVar> readVars; // variables read during critical section
	public HashSet<ShadowVar> writeVars; // variables written during critical section
	public WeakIdentityHashMap<ShadowVar,CV> wcpReadMap;
	public WeakIdentityHashMap<ShadowVar,CV> wcpWriteMap;
	public WeakIdentityHashMap<ShadowVar,CVE> wdcReadMap;
	public WeakIdentityHashMap<ShadowVar,CVE> wdcWriteMap;
	public final HashMap<ShadowThread,ArrayDeque<CV>> wcpAcqQueueMap;
	public final HashMap<ShadowThread,ArrayDeque<CV>> wcpRelQueueMap;
	public final ArrayDeque<CV> wcpAcqQueueGlobal;
	public final ArrayDeque<CV> wcpRelQueueGlobal;
	
	public final HashMap<ShadowThread,PerThreadQueue<CVE>> wdcAcqQueueMap;
	public final HashMap<ShadowThread,PerThreadQueue<CVE>> wdcRelQueueMap;
	public final PerThreadQueue<CVE> wdcAcqQueueGlobal;
	public final PerThreadQueue<CVE> wdcRelQueueGlobal;
	
	public EventNode latestRelNode;

	public WDCLockData(ShadowLock ld) {
		this.peer = ld;
		this.hb = new CV(WDCTool.INIT_CV_SIZE);
		this.wcp = new CV(WDCTool.INIT_CV_SIZE);
		this.wdc = new CV(WDCTool.INIT_CV_SIZE);
		this.readVars = new HashSet<ShadowVar>();
		this.writeVars = new HashSet<ShadowVar>();
		this.wcpReadMap = new WeakIdentityHashMap<ShadowVar,CV>();
		this.wcpWriteMap = new WeakIdentityHashMap<ShadowVar,CV>();
		this.wdcReadMap = new WeakIdentityHashMap<ShadowVar,CVE>();
		this.wdcWriteMap = new WeakIdentityHashMap<ShadowVar,CVE>();
		this.wcpAcqQueueMap = new HashMap<ShadowThread,ArrayDeque<CV>>();
		this.wcpRelQueueMap = new HashMap<ShadowThread,ArrayDeque<CV>>();
		this.wcpAcqQueueGlobal = new ArrayDeque<CV>();
		this.wcpRelQueueGlobal = new ArrayDeque<CV>();
		
		this.wdcAcqQueueMap = new HashMap<ShadowThread,PerThreadQueue<CVE>>();
		this.wdcRelQueueMap = new HashMap<ShadowThread,PerThreadQueue<CVE>>();
		this.wdcAcqQueueGlobal = new PerThreadQueue<CVE>();
		this.wdcRelQueueGlobal = new PerThreadQueue<CVE>();
		
		latestRelNode = null;
	}

	@Override
	public String toString() {
		return String.format("[HB=%s] [WCP=%s] [WDC=%s]", hb, wcp, wdc);
	}
	
}
