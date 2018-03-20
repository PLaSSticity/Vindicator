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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import acme.util.Assert;
import acme.util.Util;
import rr.meta.OperationInfo;
import rr.meta.SourceLocation;
import rr.tool.RR;

/** Base class for classes whose instances can be elements of sync sets */
class Provenance {

	final SourceLocation srcLoc;
	final Provenance parent; // if THREAD or LOCK, this provenance is for a memory access; otherwise it's for a synchronization object

	static final Provenance THREAD = new Provenance(null, null);
	static final Provenance LOCK = new Provenance(null, null);

	static ConcurrentHashMap<SourceLocation,SourceLocation> allSourceLocations = new ConcurrentHashMap<SourceLocation,SourceLocation>();
	static ConcurrentHashMap<SourceLocation,SourceLocation> threadSourceLocations = new ConcurrentHashMap<SourceLocation,SourceLocation>();
	static ConcurrentHashMap<SourceLocation,SourceLocation> lockSourceLocations = new ConcurrentHashMap<SourceLocation,SourceLocation>();

	static boolean trainingMode = true;

	static HashSet<SourceLocation> allSourceLocationsFromTraining = new HashSet<SourceLocation>();
	static HashSet<SourceLocation> threadSourceLocationsFromTraining = new HashSet<SourceLocation>();
	static HashSet<SourceLocation> lockSourceLocationsFromTraining = new HashSet<SourceLocation>();
	
	private Provenance(SourceLocation srcLoc, Provenance parent) {
		this.srcLoc = srcLoc;
		this.parent = parent;
	}

	static Provenance getProvenance(OperationInfo info, Provenance parent) {
		return getProvenance(info.getLoc(), parent);
	}
	
	static Provenance getProvenance(SourceLocation srcLoc, Provenance parent) {
		if (parent == THREAD || parent == LOCK) {
			allSourceLocations.putIfAbsent(srcLoc, srcLoc);
		}
		return new Provenance(srcLoc, parent);
	}

	void markUsed() {
		if (parent == THREAD) {
			threadSourceLocations.putIfAbsent(srcLoc, srcLoc);
		} else if (parent == LOCK) {
			lockSourceLocations.putIfAbsent(srcLoc, srcLoc);
		} else {
			parent.markUsed();
		}
	}

	static void initFile() {
		File file = null;
		if (RR.goldilocksTrainingDataDir.get() != null) {
			file = new File(RR.goldilocksTrainingDataDir.get(), "sites.txt");
			Assert.assertTrue(file.exists());
			trainingMode = false; // default is true
		}
		if (!trainingMode) {
			try {
				ObjectInputStream s = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
				SourceLocation srcLoc;
				while ((srcLoc = (SourceLocation)s.readObject()) != null) {
					allSourceLocationsFromTraining.add(srcLoc);
					if (s.readBoolean()) {
						threadSourceLocationsFromTraining.add(srcLoc);
					}
					if (s.readBoolean()) {
						lockSourceLocationsFromTraining.add(srcLoc);
					}
				}
				s.close();
			} catch (IOException e) {
				Assert.fail(e);
			} catch (ClassNotFoundException e) {
				Assert.fail(e);
			}
		}
	}
	
	static void reportUsage() {
		if (trainingMode) {
			try {
				ObjectOutputStream s = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("sites.txt")));
				for (SourceLocation srcLoc : allSourceLocations.keySet()) {
					s.writeObject(srcLoc);
					s.writeBoolean(threadSourceLocations.containsKey(srcLoc));
					s.writeBoolean(lockSourceLocations.containsKey(srcLoc));
				}
				s.writeObject(null);
				s.close();
			} catch (IOException e) {
				Assert.fail(e);
			}
		}
		
		for (SourceLocation srcLoc : allSourceLocations.keySet()) {
			Util.log("Source location: " + srcLoc + ", threadUsed = " + threadSourceLocations.containsKey(srcLoc) + ", lockUsed = " + lockSourceLocations.containsKey(srcLoc));
		}
		Util.log("Total static (access) source locations: " + allSourceLocations.size());
		Util.log("Thread-gets-used      source locations: " + threadSourceLocations.size());
		Util.log("Lock-gets-used        source locations: " + lockSourceLocations.size());
	}
	
	@Override
	public boolean equals(Object o) {
		Provenance other = (Provenance)o;
		return this.srcLoc.equals(other.srcLoc) &&
		       (this.parent == other.parent || this.parent.equals(other.parent));
	}
	
	@Override
	public int hashCode() {
		return srcLoc.hashCode() + parent.hashCode();
	}
	
	@Override
	public String toString() {
		if (this == THREAD) { return "THREAD"; }
		if (this == LOCK)   { return "LOCK"; }
		return srcLoc.toString() + " FROM " + parent;
	}
}
