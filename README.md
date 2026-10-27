# Hutao — terminal cho Android

Ứng dụng terminal cho Android, dùng bộ giả lập VT100/xterm của Termux (`terminal-view`).

## Tính năng
- Shell thật (`/system/bin/sh`) qua PTY, hỗ trợ màu 256/truecolor
- Nhiều phiên (tab): nút `+` mở phiên mới, chạm để chuyển, **giữ lâu để đóng**
- Hàng phím phụ: ESC, TAB, CTRL, ALT, mũi tên, HOME/END, PGUP/PGDN, `- / | ~ $`
- Chụm 2 ngón để phóng to/thu nhỏ chữ
- Giữ lâu trên màn hình để chọn & copy; dán từ clipboard
- Lịch sử cuộn 10.000 dòng
- Chạy nền bằng foreground service (phiên không bị hệ thống tắt)
- `$HOME` riêng (`/data/data/com.hutao.terminal/files/home`), `~/bin` nằm trong `PATH`,
  `~/.shrc` chạy khi mở shell

## Build
```
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
Cần JDK 17+ và Android SDK (platform 34). GitHub Actions cũng build APK ở mỗi lần push (tab Actions → artifact `hutao-debug-apk`).

## Chưa có
- Trình quản lý gói (`pkg install ...`): bootstrap của Termux bị gắn cứng đường dẫn
  `/data/data/com.termux`, cần tự build lại gói hoặc dùng proot + rootfs Linux.
