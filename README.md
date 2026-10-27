# Hutao — terminal cho Android

Ứng dụng terminal cho Android, dùng bộ giả lập VT100/xterm của Termux (`terminal-view`).

## Tính năng
- Shell thật (`/system/bin/sh`) qua PTY, hỗ trợ màu 256/truecolor
- Nhiều phiên (tab): nút `+` mở phiên mới, chạm để chuyển, giữ lâu để **đổi tên / đóng**
- Menu `⋮`: bật/tắt ảnh nền, font JetBrains Mono, giữ màn hình sáng, rung khi có bell,
  wake lock (tác vụ dài vẫn chạy khi tắt màn hình; cũng bật/tắt được từ thông báo)
- Hàng phím phụ: ESC, TAB, CTRL, ALT, mũi tên, HOME/END, PGUP/PGDN, `- / | ~ $`,
  `⌨` bật/tắt bàn phím, `PASTE` dán
- Phím âm lượng: Vol− = CTRL, Vol+ = ALT cho phím gõ tiếp theo
- Chụm 2 ngón để phóng to/thu nhỏ chữ (cỡ chữ được nhớ)
- Giữ lâu trên màn hình để chọn & copy; dán từ clipboard
- Lịch sử cuộn 10.000 dòng
- Chạy nền bằng foreground service (phiên không bị hệ thống tắt), nút **Thoát** trên thông báo
- Bộ nhớ máy: `~/storage` trỏ tới `/sdcard`; trong Alpine là `/sdcard` (cần cấp quyền bộ nhớ)
- **Linux đầy đủ (Alpine) với trình quản lý gói `apk`**: bấm nút `Linux` — lần đầu app tự tải
  (~4 MB, có kiểm tra SHA-256) và giải nén, sau đó mở tab Alpine chạy qua `proot`, không cần root:
  ```
  apk update && apk add python3 git nodejs gcc openssh vim
  ```
  Thư mục `$HOME` của Android được gắn vào `/mnt/hutao`. Từ shell Android, gõ `alpine` để vào.
- `$HOME` riêng (`/data/data/com.hutao.terminal/files/home`), `~/bin` nằm trong `PATH`,
  `~/.shrc` chạy khi mở shell

## Build
```
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
Cần JDK 17+ và Android SDK (platform 34). GitHub Actions cũng build APK ở mỗi lần push (tab Actions → artifact `hutao-debug-apk`).

`proot` lấy từ kho gói Termux, đóng gói sẵn trong `app/src/main/jniLibs`; cập nhật bằng
`scripts/fetch-proot.sh` (cần `curl`, `ar`, `patchelf`). Test: `./gradlew testDebugUnitTest`.

## Chưa có
- Đổi bảng màu terminal trong app

Font JetBrains Mono: SIL Open Font License 1.1 (`app/src/main/assets/fonts/OFL.txt`).
