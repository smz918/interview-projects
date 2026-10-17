#!/usr/bin/env bash
set -euo pipefail

VM_DIR="/mnt/d/smz/VMs/Windows11"
DISK="$VM_DIR/windows11.qcow2"
ISO="$VM_DIR/Windows11.iso"
OVMF_VARS="$VM_DIR/OVMF_VARS_4M.ms.fd"
TPM_DIR="$VM_DIR/tpmstate"
TPM_SOCK="/tmp/win11-swtpm-${UID}.sock"
TPM_PID="$VM_DIR/swtpm.pid"

if [[ ! -e /dev/kvm || ! -r /dev/kvm || ! -w /dev/kvm ]]; then
  echo "KVM is not accessible. Restart Ubuntu once, then try again."
  exit 1
fi
if [[ ! -f "$OVMF_VARS" ]]; then
  cp /usr/share/OVMF/OVMF_VARS_4M.ms.fd "$OVMF_VARS"
fi
mkdir -p "$TPM_DIR"
rm -f "$TPM_SOCK"
swtpm socket --tpm2 --tpmstate dir="$TPM_DIR" \
  --ctrl type=unixio,path="$TPM_SOCK" --daemon --pid file="$TPM_PID"
cleanup() {
  if [[ -f "$TPM_PID" ]]; then
    kill "$(cat "$TPM_PID")" 2>/dev/null || true
    rm -f "$TPM_PID"
  fi
  rm -f "$TPM_SOCK"
}
trap cleanup EXIT

args=(
  -enable-kvm -machine q35,smm=on -cpu host -smp 4 -m 8192
  -drive if=pflash,format=raw,readonly=on,file=/usr/share/OVMF/OVMF_CODE_4M.ms.fd
  -drive "if=pflash,format=raw,file=$OVMF_VARS"
  -chardev "socket,id=chrtpm,path=$TPM_SOCK"
  -tpmdev emulator,id=tpm0,chardev=chrtpm
  -device tpm-tis,tpmdev=tpm0
  -drive "if=none,id=osdisk,file=$DISK,format=qcow2"
  -device nvme,drive=osdisk,serial=WIN11WSL0001
  -nic user,model=e1000e
  -boot order=c,menu=on -display sdl -vga std -usb
)

if [[ -f "$ISO" ]]; then
  args+=( -cdrom "$ISO" )
else
  echo "No Windows ISO found at: $ISO"
  echo "Place the official Windows 11 ISO there to begin installation."
fi

qemu-system-x86_64 "${args[@]}"
