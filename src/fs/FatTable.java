package fs;

import java.io.IOException;
import java.util.Arrays;

/**
 * FAT 文件分配表。内存中保留 128 个表项，修改后整块写回。
 */
public class FatTable {
    private final Disk disk;
    private final byte[] entries = new byte[BootBlock.TOTAL_BLOCKS];

    public FatTable(Disk disk) {
        this.disk = disk;
    }

    public void initialize() throws IOException {
        Arrays.fill(entries, (byte) 0);
        entries[0] = (byte) BootBlock.FAT_EOF;
        entries[1] = (byte) BootBlock.FAT_EOF;
        entries[2] = (byte) BootBlock.FAT_EOF;
        entries[23] = (byte) BootBlock.FAT_BAD;
        entries[49] = (byte) BootBlock.FAT_BAD;
        save();
    }

    public void load() throws IOException {
        byte[] first = disk.readBlock(0);
        byte[] second = disk.readBlock(1);
        System.arraycopy(first, 0, entries, 0, BootBlock.BLOCK_SIZE);
        System.arraycopy(second, 0, entries, BootBlock.BLOCK_SIZE, BootBlock.BLOCK_SIZE);
    }

    public void save() throws IOException {
        disk.writeBlock(0, Arrays.copyOfRange(entries, 0, BootBlock.BLOCK_SIZE));
        disk.writeBlock(1, Arrays.copyOfRange(entries, BootBlock.BLOCK_SIZE, BootBlock.TOTAL_BLOCKS));
    }

    public int get(int blockNo) {
        return entries[blockNo] & 0xFF;
    }

    public void set(int blockNo, int value) {
        entries[blockNo] = (byte) value;
    }

    public int allocate() throws IOException {
        for (int blockNo = 3; blockNo < BootBlock.TOTAL_BLOCKS; blockNo++) {
            if (get(blockNo) == BootBlock.FAT_FREE) {
                set(blockNo, BootBlock.FAT_EOF);
                save();
                return blockNo;
            }
        }
        return -1;
    }

    public void freeChain(int startBlock) throws IOException {
        int current = startBlock;
        boolean[] visited = new boolean[BootBlock.TOTAL_BLOCKS];
        while (current >= 3 && current < BootBlock.TOTAL_BLOCKS && !visited[current]) {
            visited[current] = true;
            int next = get(current);
            set(current, BootBlock.FAT_FREE);
            if (next == BootBlock.FAT_EOF || next == BootBlock.FAT_BAD || next == BootBlock.FAT_FREE) {
                break;
            }
            current = next;
        }
        save();
    }

    public int chainLength(int startBlock) {
        if (startBlock < 3 || startBlock >= BootBlock.TOTAL_BLOCKS) {
            return 0;
        }
        if (get(startBlock) == BootBlock.FAT_FREE || get(startBlock) == BootBlock.FAT_BAD) {
            return 0;
        }
        int count = 0;
        int current = startBlock;
        boolean[] visited = new boolean[BootBlock.TOTAL_BLOCKS];
        while (current >= 3 && current < BootBlock.TOTAL_BLOCKS && !visited[current]) {
            visited[current] = true;
            count++;
            int next = get(current);
            if (next == BootBlock.FAT_EOF || next == BootBlock.FAT_BAD || next == BootBlock.FAT_FREE) {
                break;
            }
            current = next;
        }
        return count;
    }

    public int countFree() {
        int count = 0;
        for (int blockNo = 3; blockNo < BootBlock.TOTAL_BLOCKS; blockNo++) {
            if (get(blockNo) == BootBlock.FAT_FREE) {
                count++;
            }
        }
        return count;
    }
}
