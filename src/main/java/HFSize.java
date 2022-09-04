/*
    Copyright Paul Janssens 2022 - (info@afront.be) - All rights reserved

    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HFSize {

    private static final int END_OF_BITMAP = 4;

    private static final int ALLOCATION_BLOCK_SIZE = 512;

    //catalog and extents file  in allocation blocks
    private static final int BLOCKS_OVERHEAD = 24;

    private static final int MAX_FILE_SIZE = (8*(END_OF_BITMAP-3) * ALLOCATION_BLOCK_SIZE - BLOCKS_OVERHEAD)* ALLOCATION_BLOCK_SIZE;

    private static final String SIG_BINHEX = "54 45 58 54  42 4e 48 51"; // BNHQ

    private static final String SIG_STUFFIT = "53 49 54 44  53 49 54 21"; // SIT!

    private static final String SIG_COMPACT = "50 41 43 54  43 50 43 54"; // CPCT

    private static final String SIG_UNKNOWN = "63 63 63 63  63 63 63 63"; // ????

    private static class FileMeta {

        final long logicalSize;

        final String originalName;

        public FileMeta(String originalName, long logicalSize) {
            this.originalName = originalName;
            this.logicalSize = logicalSize;
        }

        public String inferSignature() {
            String s = originalName.toLowerCase();
            if(s.endsWith("sit")) {
                return SIG_STUFFIT;
            } else if (s.endsWith("hqx")) {
                return SIG_BINHEX;
            } else if (s.endsWith("cpt")) {
                return SIG_COMPACT;
            } else {
                return SIG_UNKNOWN;
            }
        }
    }


    private byte[] fileContents(File f, int fileSize) throws IOException {
        try (FileInputStream fl = new FileInputStream(f)) {

            // Now creating byte array of same length as file
            byte[] contents = new byte[(int) fileSize];
            int offset = 0;
            while (offset < fileSize) {
                int additional = fl.read(contents, offset, fileSize - offset);
                if (additional == -1) {
                    throw new IOException("failed to read complete file");
                }
                offset += additional;
            }
            return contents;
        }
    }

    private byte[] fill(byte[] b, int v) {

        for(int i = 0; i< b.length; i++) {
            b[i] = 0;
        }

        return b;
    }

    private byte[] zeroBlock(int size) {
        return fill(new byte[size*512],0);
    }

    private byte[] bootBlocks() {
        byte[] block = zeroBlock(2);

        //write signature to easily identify as a raw image
        writeHex(block, 0, "4c4b");

        return block;
    }

    //argument needed to calculate number of allocation blocks
    private byte[] mdb(long fileSize) {
        byte[] block = zeroBlock(1);

        // signature
        writeHex(block, 0, "4244");
        // first block of bitmap (starting at 0)
        writeInteger(block, 14, 3);
        //number of allocation blocks, computed from physical filesize
        writeInteger(block, 18, (int) (BLOCKS_OVERHEAD + fileSize/ALLOCATION_BLOCK_SIZE));
        //allocation block size in bytes
        writeHex(block, 20, "00000200");
        //default clump size, not really needed for a read only volume
        writeHex(block, 24, "00000800");
        //first block in bitmap, so normally this is the end of the bitmap...
        writeInteger(block, 28, END_OF_BITMAP);
        //next unused id, not really important here
        writeLong(block, 30, 11);
        //unused allocation blocks
        writeInteger(block, 34, 0);
        //volume name
        writeHex(block, 36, "03 62 6f 62");
        // clump sizes in bytes, also not really needed
        writeLong(block, 74, 6144);
        writeLong(block, 78, 6144);
        //number of directories in root
        writeInteger(block, 82, 0);
        //files on volume
        writeLong(block, 84, 1);
        //directories on volume
        writeLong(block, 88, 0);
        //extents tree size and first extent records
        writeLong(block, 130, 6144);
        writeHex(block, 134, "0000 000C 0000 0000 0000 0000");
        //catalog tree size and first extent records
        writeLong(block, 146, 6144);
        writeHex(block, 150, "000C 000C 0000 0000 0000 0000");
        return block;
    }

    private byte[] bitmap() {
        return fill(new byte[512],-1);
    }

    private byte[] extentsFile() {
        byte[] block = zeroBlock(12);

        // node header
        writeHex(block, 0x000, "00000000 00000000  01 00 0003");

        // record 0
        writeHex(block, 0x00E, "0000");
        writeHex(block, 0x010, "00 00 00 00 00 00 00 00  00 00 00 00 00 00 00 00");
        // 12 nodes, of which 11 free
        writeHex(block, 0x020, "02 00 00 07 00 00 00 0c  00 00 00 0b 00 00 00 00");

        // record 1 empty

        // record 2
        writeHex(block, 0x0F8, "80");

        // record offsets
        writeHex(block, 0x1F8, "01f8 00f8 0078 000e");

        return block;
    }

    private byte[] catalogFile(FileMeta meta) {
        byte[] block = zeroBlock(12);

        // 1st node header
        writeHex(block, 0x000, "00000000 00000000  01 00 0003");

        // record 0
        // depth = 1
        writeHex(block, 0x00E, "00 01");
        // rootnode 1, 3 leaf records, first and last leaf node = 1
        writeHex(block, 0x010, "00 00 00 01 00 00 00 03  00 00 00 01 00 00 00 01");
        // 12 nodes, of which 10 free
        writeHex(block, 0x020, "02 00 00 25 00 00 00 0c  00 00 00 0a 00 00 00 00");

        // record 1 empty

        // record 2
        writeHex(block, 0x0F8, "c0");

        // record offsets
        writeHex(block, 0x1F8, "01f8 00f8 0078 000e");


        // 2nd node header
        writeHex(block, 0x200, "00000000 00000000  ff 01 0003");

        // record 0 DirectoryRecord
        // key
        writeHex(block, 0x20E, "09 00 00000001 03 62 6f 62");
        // 70 bytes data
        writeHex(block, 0x218, "01 00 0000 0001 00000002 630bd4cc 630bd4e9 00000000 00 00");
        writeHex(block, 0x230, "00 00 00 00 00 00 01 00  00 70 02 48 00 00 00 00");
        // following bytes are zero


        // record 1 ThreadRecord
        // key
        writeHex(block, 0x25E, "07 00 00000002 00 00");
        // data
        writeHex(block, 0x266, "03 00  00 00 00 00 00 00 00 00");
        writeHex(block, 0x270, "00000001 03 62 6f 62");
        // following bytes are zero


        // record 2 filerecord
        // key
        writeHex(block, 0x294, "0d 00 00000002 07 76 69 6e 74 61 67 65");

        // filerecord identifier
        writeUnsignedByte(block, 0x2A2, 2);
        // flags
        writeUnsignedByte(block, 0x2A4, 0);
        //file type
        writeUnsignedByte(block, 0x2A5, 0xc4);

        // creator and type
        writeHex(block, 0x2A6, meta.inferSignature());
        //this is where the invisibility flag is set if needed
        writeHex(block, 0x2AE, "01 00  00 00 00 00 00 00");

        writeHex(block, 0x2B6, "00 00  00 10");

        long physicalSize =  roundToPhysicalSize(meta.logicalSize);
        int allocationBlocks =  (int) (physicalSize/ALLOCATION_BLOCK_SIZE);

        // first allocation block data fork
        writeHex(block, 0x2BA, "00 00");
        // logical length data fork
        writeLong(block, 0x2BC, meta.logicalSize);
        // physical length data fork
        writeLong(block, 0x2C0, physicalSize);

        // first allocation block resource fork
        writeHex(block, 0x2C4, "00 00");
        // logical length resource fork
        writeLong(block, 0x2C6, 0L);
        // physical length resource fork
        writeLong(block, 0x2CA, 0L);

        // creation date
        writeHex(block, 0x2CE, "63 0b d4 e9");
        // modification date
        writeHex(block, 0x2D2, "63 0b d5 1e");
        // backup
        writeHex(block, 0x2D6, "00 00 00 00");
        // 16 bytes for finder

        // clump size
        writeHex(block, 0x2EA, "00 00");

        // first extents data fork
        writeHex(block, 0x2EC, "00 18");
        writeInteger(block, 0x2EE, allocationBlocks);
        // 2nd and 3rd extent
        writeHex(block, 0x2F0, "00 00 00 00 00 00 00 00 00 00");

        // first three extents resource fork
        writeHex(block, 0x2F8, "00 18 00 01 00 00 00 00 00 00 00 00");

        // record offsets 2nd node
        writeHex(block, 0x3f8, "0108 0094 005e 000e");

        return block;
    }


    private void writeSignedByte(byte[] storage, int offset, int value) {

        storage[offset] = (byte) value;
    }

    private void writeUnsignedByte(byte[] storage, int offset, int value) {

        writeSignedByte(storage, offset, (value>127)?value-256:value);
    }

    private void writeInteger(byte[] storage, int offset, int value) {

        int hiByte = (value & 0xff00) >> 8;
        int loByte = (value & 0x00ff);
        writeSignedByte(storage, offset+0, hiByte);
        writeSignedByte(storage, offset+1, loByte);
    }

    private void writeLong(byte[] storage, int offset, long value) {

        int b3 = (int) (value & 0xff000000) >> 24;
        int b2 = (int) (value & 0x00ff0000) >> 16;
        int b1 = (int) (value & 0x0000ff00) >> 8;
        int b0 = (int) (value & 0x000000ff);
        writeSignedByte(storage, offset+0, b3);
        writeSignedByte(storage, offset+1, b2);
        writeSignedByte(storage, offset+2, b1);
        writeSignedByte(storage, offset+3, b0);
    }


    private void writeHex(byte[] storage, int offset, String arg) {
        String hex = arg.replace(" ","");
        if(hex.length()%2 ==1) {
            throw new IllegalArgumentException("invalid size of hex String");
        }

        for(int i=0; i<hex.length()/2;i++) {
            int val = Integer.parseInt(hex.substring(2*i, 2*i+2), 16);
            writeUnsignedByte(storage, offset+i, val);
        }
    }


    private void write(FileOutputStream s, byte[] data) throws IOException {
        s.write(data);
    }

    private long roundToPhysicalSize(long logicalSize) {
        return  ALLOCATION_BLOCK_SIZE * ((logicalSize+(ALLOCATION_BLOCK_SIZE-1))/ALLOCATION_BLOCK_SIZE);
    }

    private void writeVolumeImage(byte[] contents, FileMeta meta) throws IOException {
        long logicalSize = contents.length;
        long physicalSize =  roundToPhysicalSize(logicalSize);

        File g = new File("volume.img");
        try(FileOutputStream s = new FileOutputStream(g))      {

            write(s,bootBlocks());
            write(s,mdb(physicalSize));
            write(s,bitmap());
            write(s,extentsFile());
            write(s,catalogFile(meta));

            write(s,contents);

            if(physicalSize > logicalSize) {
                byte [] padding = new byte[(int) (physicalSize-logicalSize)];
                write(s,padding);
            }
        }
    }

    private void hfsize(String path) throws IOException {
        File f = new File(path);

        if(f.exists() && !f.isDirectory()) {
            Path p = Paths.get(path);
            long fileSize = Files.size(p);

            if(fileSize>MAX_FILE_SIZE) {
                throw new IllegalArgumentException("file too large");
            }

            byte[] contents = fileContents(f, (int) fileSize);

            FileMeta meta = new FileMeta(path, fileSize);

            writeVolumeImage(contents, meta);
        }
    }
    private static void verify(String[] argc) {
        if (argc.length < 1) {
            throw new IllegalArgumentException();
        }
    }

    public static void main(String[] argc) throws IOException {

        //check arguments
        verify(argc);

        //read in argument
        String path = argc[0];

        new HFSize().hfsize(path);
    }
}
