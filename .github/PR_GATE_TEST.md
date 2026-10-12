# PR thử nghiệm: bật PR này để kiểm chứng CI và branch protection
# Tạo PR từ nhánh feature vào dev/main với lỗi cố tình để kiểm tra
# Xem hướng dẫn đầy đủ trong README.md

## Cách kiểm chứng PR thất bại:

### 1. Tạo nhánh thử từ dev
```bash
git checkout dev
git checkout -b test/ci-gate-check
```

### 2. Thêm lỗi lint cố tình vào frontend
```bash
# Thêm biến unused vào bất kỳ file .tsx nào:
echo "const _unused: any = 'lint-error'" >> frontend/src/main.tsx
git add . && git commit -m "test: introduce intentional lint error"
git push origin test/ci-gate-check
```

### 3. Mở PR → GitHub Actions sẽ chạy
- Frontend CI job sẽ thất bại ở bước "Lint"
- PR không thể merge (nút Merge bị disable)
- Cần fix lint + ≥1 reviewer approval mới merge được

### 4. Fix và verify
```bash
git revert HEAD
git push origin test/ci-gate-check
# CI xanh → PR có thể merge sau khi có 1 approval
```
