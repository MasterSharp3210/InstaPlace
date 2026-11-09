#include "pch.h"
#include <jni.h>
#include <windows.h>
#include <thread>
#include <chrono>

JavaVM* g_vm = nullptr;
jclass g_mcClass = nullptr;
jmethodID g_getMinecraftMethod = nullptr;
jfieldID g_delayField = nullptr;

void WorkerThread() {
    std::this_thread::sleep_for(std::chrono::seconds(5));

    JNIEnv* env = nullptr;
    if (g_vm->AttachCurrentThread((void**)&env, nullptr) != JNI_OK || !env) {
        return;
    }

    jclass launchClass = env->FindClass("net/minecraft/launchwrapper/Launch");
    if (!launchClass) return;

    jfieldID clField = env->GetStaticFieldID(launchClass, "classLoader", "Lnet/minecraft/launchwrapper/LaunchClassLoader;");
    if (!clField) return;

    jobject classLoader = env->GetStaticObjectField(launchClass, clField);
    if (!classLoader) return;

    jclass clClass = env->GetObjectClass(classLoader);
    jmethodID loadClassMethod = env->GetMethodID(clClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");

    jstring className = env->NewStringUTF("net.minecraft.client.Minecraft");
    jclass localMcClass = (jclass)env->CallObjectMethod(classLoader, loadClassMethod, className);

    env->DeleteLocalRef(className);
    env->DeleteLocalRef(launchClass);

    if (!localMcClass) return;

    g_mcClass = (jclass)env->NewGlobalRef(localMcClass);
    env->DeleteLocalRef(localMcClass);

    g_getMinecraftMethod = env->GetStaticMethodID(g_mcClass, "func_71410_x", "()Lnet/minecraft/client/Minecraft;");
    if (!g_getMinecraftMethod) {
        env->ExceptionClear();
        g_getMinecraftMethod = env->GetStaticMethodID(g_mcClass, "getMinecraft", "()Lnet/minecraft/client/Minecraft;");
    }

    g_delayField = env->GetFieldID(g_mcClass, "field_71467_ac", "I");
    if (!g_delayField) {
        env->ExceptionClear();
        g_delayField = env->GetFieldID(g_mcClass, "rightClickDelayTimer", "I");
    }

    if (!g_getMinecraftMethod || !g_delayField) return;

    while (true) {
        jobject mcInst = env->CallStaticObjectMethod(g_mcClass, g_getMinecraftMethod);
        if (mcInst != nullptr) {
            env->SetIntField(mcInst, g_delayField, 0);
            env->DeleteLocalRef(mcInst);
        }

        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

extern "C" __declspec(dllexport) jint JNICALL Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
    g_vm = vm;
    std::thread(WorkerThread).detach();
    return JNI_OK;
}

BOOL APIENTRY DllMain(HMODULE hModule, DWORD ul_reason_for_call, LPVOID lpReserved) {
    if (ul_reason_for_call == DLL_PROCESS_ATTACH) {
        DisableThreadLibraryCalls(hModule);
        HMODULE hJvm = GetModuleHandleA("jvm.dll");
        if (hJvm != nullptr) {
            typedef jint(JNICALL* JNI_GetCreatedJavaVMs_t)(JavaVM**, jsize, jsize*);
            JNI_GetCreatedJavaVMs_t getVMs = (JNI_GetCreatedJavaVMs_t)GetProcAddress(hJvm, "JNI_GetCreatedJavaVMs");
            if (getVMs != nullptr) {
                JavaVM* vms[1] = { nullptr };
                jsize nVMs = 0;
                if (getVMs(vms, 1, &nVMs) == JNI_OK && nVMs > 0) {
                    g_vm = vms[0];
                    std::thread(WorkerThread).detach();
                }
            }
        }
    }
    return TRUE;
}