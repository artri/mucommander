package com.mucommander.ui.components;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.filechooser.FileSystemView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VolumeSpaceLabelTest {
	private static Logger LOGGER = LoggerFactory.getLogger(VolumeSpaceLabelTest.class); 
	
    public static void main(String[] args) throws Exception {
//    	FileSystemView fsv = showFileSystemViewInfo();
//    	showFileListRootsInfo(fsv);
    	
    	showFileSystemsInfo();
    	
    	getMapMounts();
    }
    
    private static FileSystemView showFileSystemViewInfo() {
        System.out.println("File system roots returned byFileSystemView.getFileSystemView():");
        FileSystemView fsv = FileSystemView.getFileSystemView();
        File[] roots = fsv.getRoots();
        for (int i = 0; i < roots.length; i++) {
            System.out.println("Root: " + roots[i]);
        }
        
        System.out.println("Home directory: " + fsv.getHomeDirectory());
        
        return fsv;
    }
    
    public static void showFileListRootsInfo(FileSystemView fsv) {
        System.out.println("File system roots returned by File.listRoots():");
        File[] f = File.listRoots();
        for (int i = 0; i < f.length; i++) {
            System.out.println("Drive: " + f[i]);
            System.out.println("Display name: " + fsv.getSystemDisplayName(f[i]));
            System.out.println("Is drive: " + fsv.isDrive(f[i]));
            System.out.println("Is floppy: " + fsv.isFloppyDrive(f[i]));
            System.out.println("Readable: " + f[i].canRead());
            System.out.println("Writable: " + f[i].canWrite());
            System.out.println("Total space: " + f[i].getTotalSpace());
            System.out.println("Usable space: " + f[i].getUsableSpace());
        }
    }
    
    public static void showFileSystemsInfo() throws Exception {
    	Iterable<Path> pathi = FileSystems.getDefault().getRootDirectories();
    	for (Path path : pathi) {
    		System.out.println("Path: " + path);
    		System.out.println("Root: " + path.getRoot());
    	}
    	
    	Iterable<FileStore> fsi = FileSystems.getDefault().getFileStores();
    	for (FileStore fs : fsi) {
    		System.out.println("FileStore: " + fs);
    		System.out.println("Name: " + fs.name());
    		System.out.println("Type: " + fs.type());
    		// available
    		System.out.println("Usable space: " + fs.getUsableSpace());
    		// total
    		System.out.println("Total space: " + fs.getTotalSpace());
    		// used
    		System.out.println("Used space: " + (fs.getTotalSpace() - fs.getUnallocatedSpace()));
    	}
    }
    
    public static List<Map<String, String>> getMapMounts() {
        List<Map<String, String>> resultList = new ArrayList<>();
        for (String mountPoint : getHDDPartitions()) {
            Map<String, String> result = new HashMap<>();
            String[] mount = mountPoint.split("\t");
            result.put("FileSystem", mount[2]);
            result.put("MountPoint", mount[1]);
            result.put("Permissions", mount[3]);
            result.put("User", mount[4]);
            result.put("Group", mount[5]);
            result.put("Total", String.valueOf(new File(mount[1]).getTotalSpace()));
            result.put("Free", String.valueOf(new File(mount[1]).getFreeSpace()));
            result.put("Used", String.valueOf(new File(mount[1]).getTotalSpace() - new File(mount[1]).getFreeSpace()));
            result.put("Free Percent", String.valueOf(getFreeSpacePercent(new File(mount[1]).getTotalSpace(), new File(mount[1]).getFreeSpace())));
            resultList.add(result);
        }
        return resultList;
    }
    
    public static List<String> getHDDPartitions() {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/mounts"), "UTF-8"));
            String response;
            StringBuilder stringBuilder = new StringBuilder();
            while ((response = bufferedReader.readLine()) != null) {
                stringBuilder.append(response.replaceAll(" +", "\t") + "\n");
            }
            bufferedReader.close();
            return Arrays.asList(stringBuilder.toString().split("\n"));
        } catch (IOException e) {
            LOGGER.error("{}", e);
        }
        return null;
    }

    private static Integer getFreeSpacePercent(long total, long free) {
        Double result = (Double.longBitsToDouble(free) / Double.longBitsToDouble(total)) * 100;
        return result.intValue();
    }    
}