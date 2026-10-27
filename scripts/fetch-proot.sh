#!/bin/sh
# Downloads Termux's proot build and packs it into app/src/main/jniLibs.
# Files are renamed lib*.so so Android extracts them to nativeLibraryDir, the one app dir that stays executable.
# Needs: curl, ar (binutils), tar, patchelf.
set -eu
REPO=https://packages.termux.dev/apt/termux-main
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/jniLibs"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

for pair in aarch64:arm64-v8a arm:armeabi-v7a x86_64:x86_64 i686:x86; do
    arch=${pair%%:*} abi=${pair#*:}
    curl -sSf "$REPO/dists/stable/main/binary-$arch/Packages.gz" | gunzip > "$WORK/Packages"
    mkdir -p "$WORK/$arch" "$OUT/$abi"
    for pkg in proot libtalloc libandroid-shmem; do
        file=$(awk -v p="$pkg" '$1=="Package:"{cur=$2} cur==p && $1=="Filename:"{print $2; exit}' "$WORK/Packages")
        curl -sSfo "$WORK/$pkg.deb" "$REPO/$file"
        (cd "$WORK" && ar x "$pkg.deb" data.tar.xz && tar -xJf data.tar.xz -C "$arch" && rm data.tar.xz)
    done
    usr="$WORK/$arch/data/data/com.termux/files/usr"
    cp "$usr/bin/proot" "$OUT/$abi/libproot.so"
    cp "$usr/libexec/proot/loader" "$OUT/$abi/libproot-loader.so"
    [ -f "$usr/libexec/proot/loader32" ] && cp "$usr/libexec/proot/loader32" "$OUT/$abi/libproot-loader32.so"
    cp "$(readlink -f "$usr/lib/libtalloc.so.2")" "$OUT/$abi/libtalloc.so"
    cp "$usr/lib/libandroid-shmem.so" "$OUT/$abi/libandroid-shmem.so"
    patchelf --replace-needed libtalloc.so.2 libtalloc.so "$OUT/$abi/libproot.so"
    patchelf --set-soname libtalloc.so "$OUT/$abi/libtalloc.so"
    for f in "$OUT/$abi"/lib*.so; do patchelf --remove-rpath "$f" 2>/dev/null || true; done
    echo "$abi: ok"
done
