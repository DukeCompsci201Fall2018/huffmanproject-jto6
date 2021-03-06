import java.util.*;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] encodings = new String[ALPH_SIZE + 1];
		encodings = makeCodingsFromTree(root, "", encodings);

		out.writeBits(BITS_PER_INT, HUFF_TREE);

		writeHeader(root, out);
		in.reset();
		writeCompressedBits(encodings, in, out);
		out.close();
		/*while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();*/
	}

	private int[] readForCounts(BitInputStream in) {
		int[] charCounts = new int[ALPH_SIZE + 1];
		int charVal = 0;
		while(true) {
			charVal = in.readBits(BITS_PER_WORD);
			if(charVal == -1) {
				break;
			}
			charCounts[charVal] = charCounts[charVal] + 1;
		}
		charCounts[PSEUDO_EOF] = 1;
		return charCounts;
	}

	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<HuffNode>();

		for(int i = 0; i < counts.length; i++) {
			if(counts[i] > 0) {
				pq.add(new HuffNode(i, counts[i]));
			}
		}
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		return pq.remove();
	}

	private String[] makeCodingsFromTree(HuffNode root, String path, String[] encodings) {
		codingHelper(root, "", encodings);	//i need to learn how to read directions and include things they tell me to include
		return encodings;
	}
	
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root != null) {
			if(root.myLeft == null && root.myRight == null) {	//current.myValue == 0 was my old check and that misses the encoding for 0 oops
				int leafVal = root.myValue;						//have to check for no children instead of myValue
				encodings[leafVal] = path;
			}
			else {
				String pathLeft = path + "0";
				String pathRight = path + "1";
				codingHelper(root.myLeft, pathLeft, encodings);
				codingHelper(root.myRight, pathRight, encodings);
			}
		}
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		HuffNode current = root;
		if(current != null) {
			if(current.myRight == null && current.myLeft == null) {	//same issue as above
				out.writeBits(1, 1);
				out.writeBits(BITS_PER_WORD + 1, current.myValue);
			}
			else {
				out.writeBits(1, 0);
				writeHeader(current.myLeft, out);
				writeHeader(current.myRight, out);
			}
		}
	}

	private void writeCompressedBits(String[] encodings, BitInputStream in, BitOutputStream out) {
		while(true) {
			int charVal = in.readBits(BITS_PER_WORD);
			if (charVal != -1) {
				String code = encodings[charVal];
				out.writeBits(code.length(), Integer.parseInt(code, 2));
			}
			if (charVal == -1) {
				String code = encodings[PSEUDO_EOF];
				out.writeBits(code.length(), Integer.parseInt(code, 2));
				break;
			}
		}
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		System.out.println(HUFF_TREE);
		int bits = in.readBits(BITS_PER_INT);
		System.out.println(bits);
		if (bits != HUFF_TREE) {
			throw new HuffException ("illegal header starts with " + bits);
		}

		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();

		/*while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();*/
	}

	private HuffNode readTreeHeader(BitInputStream in) {
		int current = in.readBits(1);
		if (current == -1) {
			throw new HuffException ("Tree contains nonvalue.");
		}

		if(current == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		}

		else {
			int val = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(val, 0, null, null);
		}
	}

	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while(true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO EOF");
			}
			else {
				if (bits == 0) {
					current = current.myLeft;
				}
				else {
					current = current.myRight;
				}
				if(current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}