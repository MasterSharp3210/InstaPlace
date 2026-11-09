import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.ptr.IntByReference;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Scanner;

public class Main {
    public interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        Pointer OpenProcess(int dwDesiredAccess, boolean bInheritHandle, int dwProcessId);
        Pointer VirtualAllocEx(Pointer hProcess, Pointer lpAddress, int dwSize, int flAllocationType, int flProtect);
        boolean WriteProcessMemory(Pointer hProcess, Pointer lpBaseAddress, byte[] lpBuffer, int nSize, IntByReference lpNumberOfBytesWritten);
        Pointer GetModuleHandleA(String lpModuleName);
        Pointer GetProcAddress(Pointer hModule, String lpProcName);
        Pointer CreateRemoteThread(Pointer hProcess, Pointer lpThreadAttributes, int dwStackSize, Pointer lpStartAddress, Pointer lpParameter, int dwCreationFlags, IntByReference lpThreadId);
        boolean CloseHandle(Pointer hObject);
    }

    public static void main(String[] args) {
        try {
            if (System.console() == null) {
                String jarPath = new File(Main.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).getAbsolutePath();
                
                String command = "Start-Process 'java.exe' -ArgumentList '-jar', '\"" + jarPath + "\"' -Verb RunAs";
                ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", command);
                pb.start();
                System.exit(0);
                return;
            }

            execute();

        } catch (Exception e) {
            System.out.println("Error during initialization or UAC elevation.");
            e.printStackTrace();
        }
    }

    private static void execute() {
        System.out.println("Waiting javaw.exe (Minecraft)");

        try {
            int pid = -1;
            while (pid == -1) {
                pid = findProcessId("javaw.exe");
                if (pid == -1) {
                    Thread.sleep(2000);
                }
            }

            System.out.println("Minecraft found! PID: " + pid);

            File dllFile = new File("InstaPlace.dll");
            if (!dllFile.exists()) {
                System.out.println("Error: 'InstaPlace.dll' not found!");
                Scanner scanner = new Scanner(System.in);
                System.out.println("Press Enter to exit...");
                scanner.nextLine();
                scanner.close();
                System.exit(0);
                return;
            }

            injectDll(pid, dllFile.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    private static int findProcessId(String processName) {
        try {
            Process process = new ProcessBuilder("tasklist", "/fi", "imagename eq " + processName, "/fo", "csv", "/nh").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains(processName.toLowerCase())) {
                    String[] parts = line.split("\",\"");
                    if (parts.length > 1) {
                        return Integer.parseInt(parts[1].replace("\"", "").trim());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static void injectDll(int pid, String dllPath) {
        Kernel32 kernel32 = Kernel32.INSTANCE;

        int ACCESS_FLAGS = 0x001F0FFF; 
        Pointer hProcess = kernel32.OpenProcess(ACCESS_FLAGS, false, pid);
        if (hProcess == null) {
            System.out.println("Failed to open process. Error code: " + Native.getLastError());
            return;
        }

        byte[] dllPathBytes = (dllPath + "\0").getBytes();
        Pointer pDllPath = kernel32.VirtualAllocEx(hProcess, null, dllPathBytes.length, 0x1000 | 0x2000, 0x40);
        if (pDllPath == null) {
            System.out.println("Failed to allocate remote memory. Error code: " + Native.getLastError());
            kernel32.CloseHandle(hProcess);
            return;
        }

        boolean written = kernel32.WriteProcessMemory(hProcess, pDllPath, dllPathBytes, dllPathBytes.length, null);
        if (!written) {
            System.out.println("Failed to write memory. Error code: " + Native.getLastError());
            kernel32.CloseHandle(hProcess);
            return;
        }

        Pointer hKernel32 = kernel32.GetModuleHandleA("kernel32.dll");
        Pointer pLoadLibrary = kernel32.GetProcAddress(hKernel32, "LoadLibraryA");

        Pointer hThread = kernel32.CreateRemoteThread(hProcess, null, 0, pLoadLibrary, pDllPath, 0, null);
        if (hThread == null) {
            System.out.println("Failed to create remote thread. Error code: " + Native.getLastError());
        } else {
            System.out.println("DLL injected successfully!");
            kernel32.CloseHandle(hThread);
        }

        kernel32.CloseHandle(hProcess);
    }
}