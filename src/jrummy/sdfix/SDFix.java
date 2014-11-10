package jrummy.sdfix;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import android.content.Context;
import android.util.Log;

public class SDFix {

	private static final String TAG = SDFix.class.getName();

	public static final String PLATFORM_XML = "/system/etc/permissions/platform.xml";

	/* Modified: Matthew Ng; the name is modified because it does not truly say if writable, for example
	 *                       the fix is completed but user has not restarted device yet.
	 */
	public static boolean isRemovableStorageWritableFixApplied() throws IOException, FileNotFoundException {
		boolean isRemovableStorageWritable = false;
		final BufferedReader reader = new BufferedReader(new FileReader(PLATFORM_XML));
		boolean isPermissionLine = false;
		String line = reader.readLine();
		while (line != null) {
			if (line.contains("android.permission.WRITE_EXTERNAL_STORAGE")) {
				isPermissionLine = true;
			}
			else if (isPermissionLine) {
				if (line.contains("</permission>")) {
					isPermissionLine = false;
				}
				else if (line.contains("media_rw")) {
					isRemovableStorageWritable = true;
				}
			}
			line = reader.readLine();
		}
		reader.close();
		return isRemovableStorageWritable;
	}

	public static String getModifiedPlatformXml() throws IOException, FileNotFoundException {
		final StringBuilder sb = new StringBuilder();
		final BufferedReader reader = new BufferedReader(new FileReader(PLATFORM_XML));
		boolean addLine = false;
		String line = reader.readLine();
		while (line != null) {
			if (line.contains("android.permission.WRITE_EXTERNAL_STORAGE")) {
				addLine = true;
			}
			else if (addLine) {
				if (line.contains("</permission>")) {
					sb.append("        <group gid=\"media_rw\" />");
					sb.append('\n');
					addLine = false;
				}
			}
			sb.append(line);
			sb.append('\n');
			line = reader.readLine();
		}
		reader.close();
		return sb.toString();
	}

	public static boolean writeModifiedPlatformXml(File outfile) throws IOException {
		String text = "";
		try {
			text = getModifiedPlatformXml();
		} catch (Exception e) {
			return false;
		}
		final BufferedWriter writer = new BufferedWriter(new FileWriter(outfile));
		writer.append(text);
		writer.close();
		return true;
	}

	public static boolean fixPermissions(Context context) throws FileNotFoundException, IOException {
		if (isRemovableStorageWritableFixApplied()) {
			Log.i(TAG, PLATFORM_XML + " is already modified to allow apps to write to a removable SD card");
			return true;
		}

		final File outfile = new File(context.getFilesDir(), "platform.xml");
		try {
			if (!writeModifiedPlatformXml(outfile)) {
				return false;
			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to write the modified file to " + outfile, e);
			return false;
		}

		if (!Remounter.mountSystemRw()) {
			Log.i(TAG, "Failed to mount system read/write");
			return false;
		}

		String[] commands = {
				"cp " + PLATFORM_XML + " /system/etc/permissions/platform.xml.bak",
				"cp \"" + outfile + "\" \"" + PLATFORM_XML + "\"",
				"chmod 0644 " + PLATFORM_XML,
				"chmod 0644 /system/etc/permissions/platform.xml.bak"
		};

		if (!Shell.SU.run(commands).success()) {
			Log.i(TAG, "Failed to copy over " + outfile + " to " + PLATFORM_XML);
			return false;
		}

		Remounter.mountSystemRo();

		return true;
	}
}
