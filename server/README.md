# server（已迁移）

PC 端本地工具已迁移至 **[`pc_tools/`](../pc_tools/)**，统一命令为 **`platalea`**。

```bash
pip install -e pc_tools/
platalea init-config
platalea start
platalea list-boards
platalea obfuscate secret.txt -p mypass
```

旧命令 `lmserver` 仍可用（已弃用，会提示改用 `platalea`）。

详见 [`pc_tools/README.md`](../pc_tools/README.md)。
