package fs;

/**
 * 已打开文件表，最多 5 项。
 */
public class OFT {
    public static final int READ = 0;
    public static final int WRITE = 1;

    public static class Pointer {
        public int dnum;
        public int bnum;

        public Pointer(int dnum, int bnum) {
            this.dnum = dnum;
            this.bnum = bnum;
        }
    }

    public static class OFTLE {
        public final String name;
        public final int attribute;
        public final int number;
        public final int length;
        public int flag;
        public Pointer read;
        public Pointer write;

        public OFTLE(String name, int attribute, int number, int length, int flag) {
            this.name = name;
            this.attribute = attribute;
            this.number = number;
            this.length = length;
            this.flag = flag;
        }
    }

    private final OFTLE[] files = new OFTLE[BootBlock.MAX_OPEN_FILES];
    private int count;

    public OFTLE find(String name) {
        for (int i = 0; i < count; i++) {
            if (files[i].name.equals(name)) {
                return files[i];
            }
        }
        return null;
    }

    public boolean isOpen(String name) {
        return find(name) != null;
    }

    public int count() {
        return count;
    }

    public OFTLE get(int index) {
        return files[index];
    }

    public boolean add(OFTLE file) {
        if (count >= BootBlock.MAX_OPEN_FILES) {
            return false;
        }
        files[count++] = file;
        return true;
    }

    public void remove(String name) {
        for (int i = 0; i < count; i++) {
            if (files[i].name.equals(name)) {
                for (int j = i; j < count - 1; j++) {
                    files[j] = files[j + 1];
                }
                files[--count] = null;
                return;
            }
        }
    }
}
