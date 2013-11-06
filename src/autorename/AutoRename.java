package autorename;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AutoRename implements Serializable {

	transient private final MapComparator COMPARATOR = new MapComparator();
	private Map<String, String> replaceMap = new TreeMap<>(COMPARATOR);
	transient private FileNameProcessor fileNameProcessor = new FileNameProcessor();

	AutoRename() {
		for (char c = 'a'; c <= 'z'; c++) {
			replaceMap.put(String.valueOf(c), String.valueOf(c));
		}
		for (char c = 'A'; c <= 'Z'; c++) {
			replaceMap.put(String.valueOf(c), String.valueOf(c));
		}
		for (char c = '0'; c <= '9'; c++) {
			replaceMap.put(String.valueOf(c), String.valueOf(c));
		}
		replaceMap.put(".", ".");
	}

	void saveMap() throws IOException {
		File file = new File("replaceMap.dat");
		XMLEncoder out = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(file)));
		try {
			out.writeObject(replaceMap);
		} finally {
			out.close();
		}
		println("replaceMap saved in {0}", file.getCanonicalPath());
	}

	void loadMap() throws IOException, ClassNotFoundException {
		File file = new File("replaceMap.dat");
		println("loading from {0}", file.getCanonicalPath());
		if (!file.exists()) {
			println("not found {0}", file.getCanonicalPath());
			return;
		}
		XMLDecoder in = new XMLDecoder(new BufferedInputStream(new FileInputStream(file)));
		try {
			replaceMap = (TreeMap<String, String>) in.readObject();
		} finally {
			in.close();
		}
		println("replaceMap loaded from {0}", file.getCanonicalPath());
	}

	static String readLine() {
		BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
		try {
			String response = bufferRead.readLine();
			return response;
		} catch (IOException ex) {
			return null;
		}
	}

	static void print(String format, Object... args) {
		if (args == null || args.length == 0) {
			System.out.print(format);
		} else {
			System.out.print(MessageFormat.format(format, args));
		}
	}

	static void println(String format, Object... args) {
		print(format + '\n', args);
	}

	private class MapComparator implements Serializable, Comparator<String> {

		@Override
		public int compare(String o1, String o2) {
			if (o1.length() > o2.length()) {
				return -1;
			}
			if (o1.length() < o2.length()) {
				return 1;
			}
			return o1.compareTo(o2);
		}
	}

	private class FileNameProcessor {

		private Set<String> findNotListed(String name, Map<String, String> map) {
			String temp = name;
			Set<String> result = new TreeSet<>(COMPARATOR);

			for (Entry<String, String> replace : map.entrySet()) {
				if (temp.contains(replace.getKey())) {
					temp = temp.replace(replace.getKey(), "");
				}
			}

			for (char c : temp.toCharArray()) {
				result.add(String.valueOf(c));
			}

			return result;
		}

		private void addNewRule(Map<String, String> map, String defaultFrom) {
			println("adding new rule");
			print("replace from [\"{0}\" {1}]: ", defaultFrom, Arrays.toString(defaultFrom.getBytes()));
			String responseFrom = readLine();
			if ("".equals(responseFrom)) {
				responseFrom = defaultFrom;
			}
			print("replace to: ");
			String responseTo = readLine();
			if (responseFrom != null && !responseFrom.isEmpty() && responseTo != null) {
				map.put(responseFrom, responseTo);
			} else {
				println("\'from\' cannot be empty, \'to\' cannot be null");
			}
		}

		private void updateReplaceMap(Map<String, String> map) {
			if (!replaceMap.equals(map)) {
				print("save changed map (y/n)? ");
				String response = readLine();
				if (response != null && ("y".equals(response.toLowerCase())
						|| "yes".equals(response.toLowerCase())
						|| "".equals(response))) {
					replaceMap = map;
				}
			}
		}

		private File renameFile(File file, String name) throws IOException {
			if (file.getName().equals(name)) {
				return file;
			} else {
				println("rename {0} => {1}", file.getName(), name);
				Path source = file.toPath();
				return Files.move(source, source.resolveSibling(name), java.nio.file.StandardCopyOption.ATOMIC_MOVE).toFile();
			}
		}

		public File process(File f) throws IOException {
			String name = f.getName();
			Set<String> notListed;
			Map<String, String> replaceMapEx = new TreeMap<>(COMPARATOR);
			replaceMapEx.putAll(replaceMap);

			do {
				notListed = findNotListed(name, replaceMapEx);
				if (!notListed.isEmpty()) {
					println("processing {0}, unknown characters {1}", f, notListed);
					addNewRule(replaceMapEx, (String) notListed.toArray()[0]);
				}
			} while (!notListed.isEmpty());

			for (Entry<String, String> replace : replaceMapEx.entrySet()) {
				if (name.contains(replace.getKey())) {
					name = name.replace(replace.getKey(), replace.getValue());
				}
			}

			File result = renameFile(f, name);

			updateReplaceMap(replaceMapEx);

			return result;
		}
	}

	private void processFilesRecursive(File dir) throws IOException {
		dir = fileNameProcessor.process(dir);
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				processFilesRecursive(f);
			} else {
				fileNameProcessor.process(f);
			}
		}
	}

	public void processArguments(String[] args) throws IOException {
		try {
			loadMap();
		} catch (Exception ex) {
			Logger.getLogger(AutoRename.class.getName()).log(Level.SEVERE, null, ex);
		}
		println(replaceMap.toString());

		if (args == null || args.length == 0) {
			args = new String[]{"."};
		}
		for (String arg : args) {
			println("scanning " + arg);
			File dir = new File(arg);
			processFilesRecursive(dir);
		}
		saveMap();
	}

	public static void main(String[] args) throws Exception {
		AutoRename rename = new AutoRename();
		rename.processArguments(args);
	}
}