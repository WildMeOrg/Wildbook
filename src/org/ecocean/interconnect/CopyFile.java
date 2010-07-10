package org.ecocean.interconnect;
import java.io.*;
import javax.swing.*;

class CopyFile {
    byte buf [] = null;
    int size = 0;
    String in = null;
    String out = null;
    
    public CopyFile(String _in, String _out) {
        in = _in;
        out = _out;
        File f = new File(in);
        size = (int) f.length();
        buf = new byte [size];
    }
    public boolean doIt() {
        File fin = new File(in);
        if(fin.isFile() == false || fin.canRead() == false) {
            JOptionPane.showMessageDialog(null, "A problem occurred when trying to read " + in);
            return false;
        }
        File fout = new File(out);
        if(fout.exists()) {
            int n = JOptionPane.showConfirmDialog(null, out + " already exists. Overwrite?",
                                                  "Overwrite?",
                                                  JOptionPane.YES_NO_OPTION);
            if(n == JOptionPane.NO_OPTION)
                return false;
        }
        
        StringBuffer mess = new StringBuffer();
        if(readFile(mess) == false) {
            JOptionPane.showMessageDialog(null, "A problem occurred when trying to read " + in);
            return false;
        }
        if(writeFile(mess) == false) {
            JOptionPane.showMessageDialog(null, "A problem occurred when trying to write " + out);
            return false;
        }
        return true;
    }
    
    public boolean writeFile(StringBuffer mess) {
        try {
            DataOutputStream dout = new DataOutputStream(new FileOutputStream(out));
            dout.write(buf, 0, size);
            dout.close();
        } catch(FileNotFoundException fnf) {
            mess.append("Could not open " + out + " for writing.");
            return false;
        } catch(IOException e) {
            mess.append("Error while writing to " + out);
            return false;
        }
        return true;
    }
    
    private boolean readFile(StringBuffer mess) {
        try {
            DataInputStream din = new DataInputStream(new FileInputStream(in));
            din.read(buf, 0, size);
            din.close();
        } catch(FileNotFoundException fnf) {
            mess.append("Could not find file " + in);
        } catch(IOException e) {
            mess.append("Error while reading from " + in);
            return false;
        }
        return true;
    }
}
