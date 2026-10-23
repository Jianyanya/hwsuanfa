# 前端

React 19、TypeScript、Vite、Tailwind CSS 3、TanStack Query、Recharts。

在本目录运行 `npm ci`、`npm run dev`，访问 `http://localhost:5173`。开发代理将 `/api` 请求转发到后端 `8080`。

检查：`npm run lint`、`npm test`、`npm run build`。生产部署使用 `dist`，配置前端路由回退和 `/api` 反向代理；配置细节见 [根目录 README](../README.md)。

主题变量在 `src/index.css`，导航在 `src/config/nav.ts`，路由在 `src/router/index.tsx`。页面按需加载，业务日期按上海时区显示。权限、积分及隐私裁剪以服务端判断为准。

不在前端源码、静态资源或环境变量中保存数据库密码、JWT 签名密钥或管理员邀请码。
