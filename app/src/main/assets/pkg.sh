#!/bin/sh
# Termux-style `pkg` on top of Alpine's apk. Installed as /usr/local/bin/pkg inside the Alpine rootfs.
usage() {
    cat <<'USAGE'
Cách dùng: pkg <lệnh> [gói...]
  install, i       cài gói (tự cập nhật danh sách gói)
  uninstall, rm    gỡ gói
  update           cập nhật danh sách gói
  upgrade          nâng cấp mọi gói
  search, s        tìm gói
  show             xem thông tin gói
  list-installed   liệt kê gói đã cài
  files            liệt kê file của gói
USAGE
}
cmd=$1
[ $# -gt 0 ] && shift
case "$cmd" in
    install|i|add) exec apk add --update-cache "$@" ;;
    uninstall|remove|rm|del) exec apk del "$@" ;;
    update|up) exec apk update ;;
    upgrade|ug) apk update && exec apk upgrade "$@" ;;
    search|s) exec apk search -v "$@" ;;
    show|info) exec apk info -a "$@" ;;
    list-installed|list|li) exec apk info -v ;;
    files) exec apk info -L "$@" ;;
    help|-h|--help|"") usage ;;
    *) echo "pkg: không có lệnh '$cmd'" >&2; usage >&2; exit 1 ;;
esac
