/*
  This file is part of JOP, the Java Optimized Processor
    see <http://www.jopdesign.com/>

  Copyright (C) 2001-2008, Martin Schoeberl (martin@jopdesign.com)

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package wcet;

import com.jopdesign.sys.Const;
import com.jopdesign.sys.Native;

public class Dispatch {

	static int ts, te, to;


	public static void main(String[] args) {

		ts = Native.rdMem(Const.IO_CNT);
		te = Native.rdMem(Const.IO_CNT);
		to = te-ts;
		A a = new A();
		B b = new B();
		C c = new C();
		
		measure(a);
		System.out.println(te-ts-to);
		measure(b);
		System.out.println(te-ts-to);
		measure(c);
		System.out.println(te-ts-to);
	}
	static void measure(A x) {
		ts = Native.rdMem(Const.IO_CNT);
		x.loop();
		te = Native.rdMem(Const.IO_CNT);		
	}
	
	static class A {
		
		void loop() {
			int val = 123;
			for (int i=0; i<10; ++i) { // @WCA loop=10
				val *= i;
			}
		}
	}
	static class B extends A {
		
		void loop() {
			int val = 123;
			for (int i=0; i<100; ++i) { // @WCA loop=100
				val *= i;
			}
		}
	}
	static class C extends B {
		
		void loop() {
			int val = 123;
			for (int i=0; i<1000; ++i) { // @WCA loop=1000
				val *= i;
			}
		}
	}

}
