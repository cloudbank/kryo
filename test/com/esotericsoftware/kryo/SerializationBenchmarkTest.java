/* Copyright (c) 2008-2017, Nathan Sweet
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package com.esotericsoftware.kryo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.minlog.Log;

/*** This test was originally taken from a GridGain blog. It is a compares the speed of serialization using Java serialization,
 * Kryo, Kryo with Unsafe patches and GridGain's serialization.
 * 
 * @author Roman Levenstein <romixlev@gmail.com> */
public class SerializationBenchmarkTest extends KryoTestCase {
	static private final int WARMUP_ITERATIONS = 1000;

	/** Number of runs. */
	static private final int RUN_CNT = 1;

	/** Number of iterations. Set it to something rather big for obtaining meaningful results */
// static private final int ITER_CNT = 200000;
	static private final int ITER_CNT = 100;

	static private final int SLEEP_BETWEEN_RUNS = 100;

	static private final int OUTPUT_BUFFER_SIZE = 4096 * 10 * 4;

	SampleObject obj = createObject();

	static private SampleObject createObject () {
		long[] longArr = new long[3000];

		for (int i = 0; i < longArr.length; i++)
			longArr[i] = i;

		double[] dblArr = new double[3000];

		for (int i = 0; i < dblArr.length; i++)
			dblArr[i] = 0.1 * i;

		return new SampleObject(123, 123.456f, (short)321, longArr, dblArr);
	}

// static public void main(String[] args) throws Exception {
// // Create sample object.
// SampleObject obj = createObject();
//
// SerializationBenchmarkTest benchmark = new SerializationBenchmarkTest();
//
// // Run Java serialization test.
// // benchmark.testJavaSerialization(obj);
// // benchmark.testJavaSerializationWithoutTryCatch(obj);
//
// // Run Kryo serialization test.
// // benchmark.kryoSerialization(obj);
// benchmark.testKryoSerializationUmodified(obj);
// benchmark.testKryoSerializationWithoutTryCatch(obj);
// benchmark.testKryoUnsafeSerializationWithoutTryCatch(obj);
//
// // Run GridGain serialization test.
// // benchmark.gridGainSerialization(obj);
// }

	public void testJavaSerialization () throws Exception {
		runJavaSerialization(1, WARMUP_ITERATIONS, false);
		runJavaSerialization(RUN_CNT, ITER_CNT, true);
	}

	public void testJavaSerializationWithoutTryCatch () throws Exception {
		runJavaSerializationWithoutTryCatch(1, WARMUP_ITERATIONS, false);
		runJavaSerializationWithoutTryCatch(RUN_CNT, ITER_CNT, true);
	}

	public void testKryoSerialization () throws Exception {
		runKryoSerialization(1, WARMUP_ITERATIONS, false);
		runKryoSerialization(RUN_CNT, ITER_CNT, true);
	}

	public void testKryoSerializationUnmodified () throws Exception {
		runKryoSerializationUmodified(1, WARMUP_ITERATIONS, false);
		runKryoSerializationUmodified(RUN_CNT, ITER_CNT, true);
	}

	public void testKryoSerializationWithoutTryCatch () throws Exception {
		runKryoSerializationWithoutTryCatch(1, WARMUP_ITERATIONS, false);
		runKryoSerializationWithoutTryCatch(RUN_CNT, ITER_CNT, true);
	}

	private void runJavaSerialization (final int RUN_CNT, final int ITER_CNT, boolean outputResults) throws Exception {
		long avgDur = 0;
		long bestTime = Long.MAX_VALUE;

		for (int i = 0; i < RUN_CNT; i++) {
			SampleObject newObj = null;

			long start = System.nanoTime();

			for (int j = 0; j < ITER_CNT; j++) {
				ByteArrayOutputStream out = new ByteArrayOutputStream(OUTPUT_BUFFER_SIZE);

				ObjectOutputStream objOut = null;

				try {
					objOut = new ObjectOutputStream(out);

					objOut.writeObject(obj);
				} finally {
					objOut.close();
					// U.close(objOut, null);
				}

				ObjectInputStream objIn = null;

				try {
					objIn = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));

					newObj = (SampleObject)objIn.readObject();
				} finally {
					objIn.close();
					// U.close(objIn, null);
				}
			}

			long dur = System.nanoTime() - start;
			dur = TimeUnit.NANOSECONDS.toMillis(dur);
			// Check that unmarshalled object is equal to original one (should
			// never fail).
			if (!obj.equals(newObj)) throw new RuntimeException("Unmarshalled object is not equal to original object.");

			if (outputResults) System.out.format(">>> Java serialization via Externalizable (run %d): %,d ms\n", i + 1, dur);
			avgDur += dur;
			bestTime = Math.min(bestTime, dur);
			systemCleanupAfterRun();
		}

		avgDur /= RUN_CNT;

		if (outputResults) {
			System.out.format("\n>>> Java serialization via Externalizable (average): %,d ms\n\n", avgDur);
			System.out.format("\n>>> Java serialization via Externalizable (best time): %,d ms\n\n", bestTime);
		}

	}

	private void systemCleanupAfterRun () throws InterruptedException {
		System.gc();
		Thread.sleep(SLEEP_BETWEEN_RUNS);
		System.gc();
	}

	private void runJavaSerializationWithoutTryCatch (final int RUN_CNT, final int ITER_CNT, boolean outputResults)
		throws Exception {
		long avgDur = 0;
		long bestTime = Long.MAX_VALUE;

		for (int i = 0; i < RUN_CNT; i++) {
			SampleObject newObj = null;

			long start = System.nanoTime();

			for (int j = 0; j < ITER_CNT; j++) {
				ByteArrayOutputStream out = new ByteArrayOutputStream(OUTPUT_BUFFER_SIZE);

				ObjectOutputStream objOut = null;

				objOut = new ObjectOutputStream(out);

				objOut.writeObject(obj);
				objOut.close();
				// U.close(objOut, null);

				ObjectInputStream objIn = null;

				objIn = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));

				newObj = (SampleObject)objIn.readObject();
				objIn.close();
				// U.close(objIn, null);
			}

			long dur = System.nanoTime() - start;
			dur = TimeUnit.NANOSECONDS.toMillis(dur);
			// Check that unmarshalled object is equal to original one (should
			// never fail).
			if (!obj.equals(newObj)) throw new RuntimeException("Unmarshalled object is not equal to original object.");
			if (outputResults) System.out.format(">>> Java serialization without try-catch (run %d): %,d ms\n", i + 1, dur);

			avgDur += dur;
			bestTime = Math.min(bestTime, dur);
			systemCleanupAfterRun();
		}

		avgDur /= RUN_CNT;

		if (outputResults) {
			System.out.format("\n>>> Java serialization without try-catch via Externalizable (average): %,d ms\n\n", avgDur);
			System.out.format("\n>>> Java serialization without try-catch via Externalizable (best time): %,d ms\n\n", bestTime);
		}
	}

	private void runKryoSerialization (final int RUN_CNT, final int ITER_CNT, boolean outputResults) throws Exception {
		Kryo marsh = new Kryo();
		marsh.setRegistrationRequired(false);
		marsh.register(SampleObject.class, 40);

		long avgDur = 0;
		long bestTime = Long.MAX_VALUE;
		byte[] out = new byte[OUTPUT_BUFFER_SIZE];

		for (int i = 0; i < RUN_CNT; i++) {
			SampleObject newObj = null;

			long start = System.nanoTime();

			for (int j = 0; j < ITER_CNT; j++) {

				Output kryoOut = null;

				try {
					kryoOut = new Output(out);
					marsh.writeObject(kryoOut, obj);
				} finally {
					kryoOut.close();
					// U.close(kryoOut, null);
				}

				Input kryoIn = null;

				try {
					kryoIn = new Input(kryoOut.getBuffer(), 0, kryoOut.position());
					newObj = marsh.readObject(kryoIn, SampleObject.class);
				} finally {
					kryoIn.close();
					// U.close(kryoIn, null);
				}
			}

			long dur = System.nanoTime() - start;
			dur = TimeUnit.NANOSECONDS.toMillis(dur);
			// Check that unmarshalled object is equal to original one (should
			// never fail).
			if (!obj.equals(newObj)) throw new RuntimeException("Unmarshalled object is not equal to original object.");

			if (outputResults) System.out.format(">>> Kryo serialization (run %d): %,d ms\n", i + 1, dur);
			avgDur += dur;
			bestTime = Math.min(bestTime, dur);
			systemCleanupAfterRun();
		}

		avgDur /= RUN_CNT;

		if (outputResults) {
			System.out.format("\n>>> Kryo serialization (average): %,d ms\n\n", avgDur);
			System.out.format("\n>>> Kryo serialization (best time): %,d ms\n\n", bestTime);
		}

	}

	private void runKryoSerializationUmodified (final int RUN_CNT, final int ITER_CNT, boolean outputResults) throws Exception {
		Kryo marsh = new Kryo();
		marsh.setRegistrationRequired(false);

		long avgDur = 0;
		long bestTime = Long.MAX_VALUE;

		for (int i = 0; i < RUN_CNT; i++) {
			SampleObject newObj = null;

			long start = System.nanoTime();

			for (int j = 0; j < ITER_CNT; j++) {
				Output kryoOut = null;

				try {
					kryoOut = new Output(OUTPUT_BUFFER_SIZE);

					marsh.writeObject(kryoOut, obj);
				} finally {
					// U.close(kryoOut, null);
					kryoOut.close();
				}

				Input kryoIn = null;

				try {
					kryoIn = new Input(kryoOut.getBuffer(), 0, kryoOut.position());
					newObj = marsh.readObject(kryoIn, SampleObject.class);
				} finally {
					// U.close(kryoIn, null);
					kryoIn.close();
				}
			}

			long dur = System.nanoTime() - start;
			dur = TimeUnit.NANOSECONDS.toMillis(dur);

			// Check that unmarshalled object is equal to original one (should
			// never fail).
			if (!obj.equals(newObj)) throw new RuntimeException("Unmarshalled object is not equal to original object.");

			if (outputResults) System.out.format(">>> Kryo unmodified serialization (run %d): %,d ms\n", i + 1, dur);

			avgDur += dur;
			bestTime = Math.min(bestTime, dur);
			systemCleanupAfterRun();
		}

		avgDur /= RUN_CNT;

		if (outputResults) {
			System.out.format("\n>>> Kryo unmodified serialization (average): %,d ms\n\n", avgDur);
			System.out.format("\n>>> Kryo unmodified serialization (best time): %,d ms\n\n", bestTime);
		}
	}

	private void runKryoSerializationWithoutTryCatch (final int RUN_CNT, final int ITER_CNT, boolean outputResults)
		throws Exception {
		Kryo marsh = new Kryo();
		marsh.setRegistrationRequired(false);
		marsh.register(SampleObject.class, 40);

		long avgDur = 0;
		long bestTime = Long.MAX_VALUE;

		byte[] out = new byte[OUTPUT_BUFFER_SIZE];

		for (int i = 0; i < RUN_CNT; i++) {
			SampleObject newObj = null;

			long start = System.nanoTime();

			for (int j = 0; j < ITER_CNT; j++) {

				Output kryoOut = null;

				kryoOut = new Output(out);

				marsh.writeObject(kryoOut, obj);
				kryoOut.close();
				// U.close(kryoOut, null);

				Input kryoIn = null;

				kryoIn = new Input(kryoOut.getBuffer(), 0, kryoOut.position());

				newObj = marsh.readObject(kryoIn, SampleObject.class);
				kryoIn.close();
				// U.close(kryoIn, null);
			}

			long dur = System.nanoTime() - start;
			dur = TimeUnit.NANOSECONDS.toMillis(dur);
			// Check that unmarshalled object is equal to original one (should
			// never fail).
			if (!obj.equals(newObj)) throw new RuntimeException("Unmarshalled object is not equal to original object.");
			if (outputResults) System.out.format(">>> Kryo serialization without try-catch (run %d): %,d ms\n", i + 1, dur);

			avgDur += dur;
			bestTime = Math.min(bestTime, dur);
			systemCleanupAfterRun();
		}

		avgDur /= RUN_CNT;

		if (outputResults) {
			System.out.format("\n>>> Kryo serialization without try-catch (average): %,d ms\n\n", avgDur);
			System.out.format("\n>>> Kryo serialization without try-catch (best time): %,d ms\n\n", bestTime);
		}
	}

	protected void setUp () throws Exception {
		super.setUp();
		Log.WARN();
	}

	static private class SampleObject implements Externalizable, KryoSerializable {
		private int intVal;
		private float floatVal;
		private Short shortVal;
		private long[] longArr;
		private double[] dblArr;
		private SampleObject selfRef;

		public SampleObject () {
		}

		SampleObject (int intVal, float floatVal, Short shortVal, long[] longArr, double[] dblArr) {
			this.intVal = intVal;
			this.floatVal = floatVal;
			this.shortVal = shortVal;
			this.longArr = longArr;
			this.dblArr = dblArr;

			selfRef = this;
		}

		/** {@inheritDoc} */

		public boolean equals (Object other) {
			if (this == other) return true;

			if (other == null || getClass() != other.getClass()) return false;

			SampleObject obj = (SampleObject)other;

			assert this == selfRef;
			assert obj == obj.selfRef;

			return intVal == obj.intVal && floatVal == obj.floatVal && shortVal.equals(obj.shortVal)
				&& Arrays.equals(dblArr, obj.dblArr) && Arrays.equals(longArr, obj.longArr);
		}

		// Required by Kryo serialization.

		public void read (Kryo kryo, Input in) {
			intVal = kryo.readObject(in, Integer.class);
			floatVal = kryo.readObject(in, Float.class);
			shortVal = kryo.readObject(in, Short.class);
			longArr = kryo.readObject(in, long[].class);
			dblArr = kryo.readObject(in, double[].class);
			selfRef = kryo.readObject(in, SampleObject.class);
		}

		// Required by Java Externalizable.

		public void readExternal (ObjectInput in) throws IOException, ClassNotFoundException {
			intVal = in.readInt();
			floatVal = in.readFloat();
			shortVal = in.readShort();
			longArr = (long[])in.readObject();
			dblArr = (double[])in.readObject();
			selfRef = (SampleObject)in.readObject();
		}

		// Required by Kryo serialization.

		public void write (Kryo kryo, Output out) {
			kryo.writeObject(out, intVal);
			kryo.writeObject(out, floatVal);
			kryo.writeObject(out, shortVal);
			kryo.writeObject(out, longArr);
			kryo.writeObject(out, dblArr);
			kryo.writeObject(out, selfRef);
		}

		// Required by Java Externalizable.

		public void writeExternal (ObjectOutput out) throws IOException {
			out.writeInt(intVal);
			out.writeFloat(floatVal);
			out.writeShort(shortVal);
			out.writeObject(longArr);
			out.writeObject(dblArr);
			out.writeObject(selfRef);
		}
	}
}
