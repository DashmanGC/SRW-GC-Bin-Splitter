/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package srwgcbinsplitter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jonatan
 */
public class Main {

    public static class IndexEntry{
        public String name;
        public int offset;
        public int size;

        public IndexEntry(){
            name = "";
            offset = 0;
            size = 0;
        }

        public IndexEntry(String n, int o, int s){
            name = n;
            offset = o;
            size = s;
        }
    }

    static String filename;
    static String destination = ".";
    static RandomAccessFile f;
    static String file_list = "";
    static byte[] seq;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        /*
         * USE
         * -s <filename> [<destination_folder>] Splits filename's contents on destination
         * -m <filename> <files_list> Merges the list of files in files_list into filename
         */

        boolean show_use = false;

        if (args.length < 2 || args.length > 3){
            show_use = true;
        }

        else{
            String command = args[0];
            filename = args[1];

            if (command.equals("-s")){

                if (args.length == 3)
                    destination = args[2];

                // Try opening the file
                try{
                    f = new RandomAccessFile(filename, "r");
                    // Read the header / index and obtain the offsets
                    readHeader();
                }catch (IOException ex) {
                    System.err.println("ERROR: Couldn't read file.");   // END
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else if (command.equals("-m")){
                if (args.length != 3)
                    show_use = true;
                else{
                    file_list = args[2];
                    // Read the file list and merge the contents into the given filename
                    try{
                        mergeFileList();
                    }catch (IOException ex) {
                        System.err.println("ERROR: Couldn't read file.");   // END
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            else    // Wrong command
                show_use = true;
        }

        if (show_use){
            System.out.println("ERROR: Wrong number of parameters: " + args.length);
            System.out.println("TO SPLIT:\n java -jar bin_splitter -s <filename> [<destination_folder>]");
            System.out.println("TO MERGE:\n java -jar bin_splitter -m <filename> <files_list>");
        }

    }

    public static void readHeader() throws IOException{
        // Read the first 5 bytes of the file
        byte[] header = new byte[5];
        f.read(header);

        // If the first two bytes are 00, we have a valid file
        if (header[0] == 0 && header[1] == 0){
            int index_size = header[3] & 0xff; // Take the lower part
            index_size += ((header[2] & 0xff) << 8);   // Add the upper part

            // If byte 4 is 20, entries are 40 bytes long (32 bytes for name, 4 bytes for offset and 4 for size)
            if (header[4] == 0x20){
                getIndex40(index_size);
            }

            // Otherwise, entries are just 4 bytes long (offsets)
            // There's a maximum of index_size / 4 entries (could be less)
            else{
                index_size = index_size / 4;
                getIndex4(index_size);
            }

            // Write the file list
            writeFileList();
        }
        // Otherwise, indicate the file is not supported
        else{
            System.err.println("ERROR: Unsupported file."); // END
            f.close();
        }
    }

    // Takes a 4-byte hex little endian and returns its int value
    public static int byteSeqToInt(byte[] byteSequence){
        if (byteSequence.length != 4)
            return -1;

        int value = 0;
        value += byteSequence[3] & 0xff;
        value += (byteSequence[2] & 0xff) << 8;
        value += (byteSequence[1] & 0xff) << 16;
        value += (byteSequence[0] & 0xff) << 24;
        return value;
    }

    // Receives an int and return its 4-byte value
    public static byte[] int2bytes(int value){
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    public static void getIndex4(int num_entries) throws IOException{
        // Prepare an arraylist of IndexEntry
        ArrayList<IndexEntry> entries = new ArrayList<IndexEntry>();

        IndexEntry ie;
        String name = "";
        int offset;
        int next;
        int size = 0;
        boolean go_on = true;

        for (int i = 0; i < num_entries && go_on; i++){
            // Every entry in the index has 4 bytes indicating its offset
            // The last entry points at an "end" file that is 32 bytes long and has nothing
            f.seek(i*4);    // Go to the beginning of our current entry
            byte[] entry_block = new byte[8];   // Read the offset of this entry and the next one
            f.read(entry_block);

            if (i < 10)
                name = "000" + i;
            else if (i < 100)
                name = "00" + i;
            else if (i < 1000)
                name = "0" + i;
            else
                name = "" + i;

            seq = new byte[4];
            seq[0] = entry_block[0];
            seq[1] = entry_block[1];
            seq[2] = entry_block[2];
            seq[3] = entry_block[3];
            offset = byteSeqToInt(seq);

            seq[0] = entry_block[4];
            seq[1] = entry_block[5];
            seq[2] = entry_block[6];
            seq[3] = entry_block[7];
            next = byteSeqToInt(seq);

            if (next == 0 || i == num_entries - 1){
                size = 32;
                go_on = false;
            }
            else
                size = next - offset;

            ie = new IndexEntry(name, offset, size);

            entries.add(ie);
        }

        // Extract every file in the final list
        for (int i = 0; i < entries.size(); i++){
            //System.out.println(i + " - Offset: " + entries.get(i).offset + " Size: " + entries.get(i).size);
            extractFile(entries.get(i));

            //file_list += entries.get(i).name;
            if (i != entries.size() - 1)
                file_list += "\n";
        }

        // Inform of results
        System.out.println("Finished. Extracted " + entries.size() + " files.");

        f.close();  // END
    }

    public static void getIndex40(int num_entries) throws IOException{
        // Prepare an arraylist of IndexEntry
        ArrayList<IndexEntry> entries = new ArrayList<IndexEntry>();

        // Start reading from the appropriate position in the file
        f.seek(4);  // Right after the number of entries

        IndexEntry ie;
        String name;
        int offset;
        int size;

        for (int i = 0; i < num_entries; i++){
            // Every entry in the index has 32 bytes for the name + 4 bytes for the offset + 4 bytes with the length of the file
            byte[] entry_block = new byte[40];
            f.read(entry_block);

            name = "";
            for (int j = 0; j < 32; j++)
                if (entry_block[j] != 0x20)
                    name += (char) entry_block[j];

            seq = new byte[4];
            seq[0] = entry_block[32];
            seq[1] = entry_block[33];
            seq[2] = entry_block[34];
            seq[3] = entry_block[35];
            offset = byteSeqToInt(seq);

            seq[0] = entry_block[36];
            seq[1] = entry_block[37];
            seq[2] = entry_block[38];
            seq[3] = entry_block[39];
            size = byteSeqToInt(seq);

            ie = new IndexEntry(name, offset, size);

            entries.add(ie);
        }

        // Extract every file in the final list
        for (int i = 0; i < entries.size(); i++){
            //System.out.println(i + " - Offset: " + entries.get(i).offset + " Size: " + entries.get(i).size);
            extractFile(entries.get(i));

            //file_list += entries.get(i).name;
            if (i != entries.size() - 1)
                file_list += "\n";
        }

        // Inform of results
        System.out.println("Finished. Extracted " + num_entries + " files.");

        f.close();  // END
    }

    public static void extractFile(IndexEntry ie) throws IOException{
        f.seek( (long) ie.offset);

        seq = new byte[ie.size];

        f.read(seq);

        String path = "";

        if (ie.name.length() == 4){ // We come from getIndex4 and haven't assigned extensions to the filenames
            if (seq[0] == 'S' && seq[1] == 'P' && seq[2] == 'R')
                ie.name += ".SPR";
            else if (seq[0] == 'S' && seq[1] == 'C' && seq[2] == 'R')
                ie.name += ".SCR";
            else if (seq[0] == 'B' && seq[1] == 'M' && seq[2] == 'P'){
                ie.name += ".BM";   // We don't use BMP because it's not exactly a bmp file
                ie.name += Integer.toString( seq[3] );
            }
            else if (seq[0] == 'E' && seq[1] == 'C' && seq[2] == 'D'){
                ie.name += ".ECD";
            }
            else if (seq[0] == 'P' && seq[1] == 'A' && seq[2] == 'T'){
                ie.name += ".PAT";
            }
            else if (seq[0] == 'A' && seq[1] == 'T' && seq[2] == 'R'){
                ie.name += ".ATR";
            }
            else if (seq[0] == 'T' && seq[1] == 'I' && seq[2] == 'M'){
                ie.name += ".TIM";
            }
            else if (seq[0] == 'e' && seq[1] == 'n' && seq[2] == 'd'){
                ie.name += ".END";
            }
            else
                ie.name += ".bin";
        }

        if (destination.equals("."))
            path = ie.name;
        else{
        // Check if folder with the name of the pak_file exists. If not, create it.
            path = destination;
            File folder = new File(path);
            if (!folder.exists()){
                boolean success = folder.mkdir();
                if (!success){
                    System.err.println("ERROR: Couldn't create folder.");
                    return;
                }
            }
            path += "/" + ie.name;
        }

        // Create the file inside said folder
        try {
            RandomAccessFile f2 = new RandomAccessFile(path, "rw");

            f2.write(seq);

            f2.close();

            file_list += ie.name;

            //System.out.println(ie.name + " saved successfully.");
        } catch (IOException ex) {
            System.err.println("ERROR: Couldn't write " + ie.name);
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void writeFileList() throws IOException{
        String path = "";
        if (destination.equals("."))
            path = "files.list";
        else    // The folder was created previously
            path = destination + "/files.list";

        PrintWriter pw = new PrintWriter(path);

        pw.print(file_list);

        pw.close();
    }

    public static void mergeFileList() throws IOException{
        BufferedReader br = new BufferedReader(new FileReader(file_list));
        String line;
        int entry_size = 4;
        int table_size = 0;
        int total_length = 0;
        ArrayList<IndexEntry> entries = new ArrayList<IndexEntry>();

        IndexEntry ie;
        boolean longEntries = false;    // Determines if the index only has offsets or includes filenames and sizes

        // Read all filenames in files.list and their sizes
        int actual_length = 0;
        int padded_length = 0;

        while ((line = br.readLine()) != null) {
            if (line.length() > 8){
                if (!line.endsWith(".BM10"))
                    longEntries = true;
            }

            f = new RandomAccessFile(line, "r");

            actual_length = (int) f.length();
            padded_length = actual_length; // Only used for long entries
            if (actual_length % 32 > 0)
                padded_length += 32 - (actual_length % 32);

            // We repurpose the offset value to store the padded length
            ie = new IndexEntry(line, padded_length, actual_length);

            entries.add(ie);
            total_length += padded_length;  // Padded length is the same as the actual length in small indexes

            f.close();
        }
        br.close();

        // For some reason, the LAST entry in bpilot.pak doesn't care about padding
        // We have to correct the length to reflect this
        // Fortunately, the length variables still have the values for the last file
        total_length -= (padded_length - actual_length);

        if (longEntries)
            entry_size = 40;

        table_size = entries.size() * entry_size;

        if (longEntries)
            table_size += 4;    // first 4 bytes have the number of entries

        // Size has to be a multiple of 32
        int extraBytes = table_size % 32;

        if (extraBytes > 0) // If we're not 32-byte aligned
            table_size += 32 - extraBytes;    // Add padding to the size

        total_length += table_size;
        seq = new byte[total_length];   // Here we'll write the full file
        byte[] aux;

        int pointer_table = 0;
        int pointer_data = table_size;  // Data starts right after the table

        if (longEntries){    // Write the number of entries in the first 4 bytes of the table
            aux = int2bytes(entries.size());
            seq[0] = aux[0];
            seq[1] = aux[1];
            seq[2] = aux[2];
            seq[3] = aux[3];
            pointer_table = 4;
        }

        // Write each of the files into seq and update its pointer in the table
        for (int i = 0; i < entries.size(); i++){
            // Update pointer
            aux = int2bytes(pointer_data);

            if (longEntries){   // Write the filename, the offset and the size of the file
                // In the name field, the bytes that don't have part of the name are filled with 0x20
                int filler = 32 - entries.get(i).name.length();
                
                for (int j = 0; j < filler; j++)
                    seq[pointer_table + j] = 0x20;

                for (int j = 0; j < entries.get(i).name.length(); j++)
                    seq[pointer_table + filler + j] = (byte) entries.get(i).name.charAt(j);

                seq[pointer_table + 32] = aux[0];
                seq[pointer_table + 33] = aux[1];
                seq[pointer_table + 34] = aux[2];
                seq[pointer_table + 35] = aux[3];

                aux = int2bytes(entries.get(i).size);
                seq[pointer_table + 36] = aux[0];
                seq[pointer_table + 37] = aux[1];
                seq[pointer_table + 38] = aux[2];
                seq[pointer_table + 39] = aux[3];

                pointer_table += 40;
            }
            else{   // Just write the offset
                seq[pointer_table] = aux[0];
                seq[pointer_table + 1] = aux[1];
                seq[pointer_table + 2] = aux[2];
                seq[pointer_table + 3] = aux[3];

                pointer_table += 4;
            }

            // Write the file into our byte sequence
            aux = new byte[entries.get(i).size];

            f = new RandomAccessFile(entries.get(i).name, "r");
            f.read(aux);
            f.close();

            for (int j = 0; j < aux.length; j++)
                seq[pointer_data + j] = aux[j];

            pointer_data += entries.get(i).offset;
        }

        // Save the byte sequence to a file
        f = new RandomAccessFile(filename, "rw");
        f.write(seq);
        f.close();

        System.out.println("Finished. File " + filename + " built successfully.");
    }
}
