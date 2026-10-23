import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Save } from 'lucide-react';

import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button, Notice } from '@/components/ui/Button';
import { adminListUsers, adminUpdateUser } from '@/api/community';
import type { AdminUserRow } from '@/api/community';

/**
 * 成员管理 —— 站主可修改成员资料（昵称/姓名/班级/handle/停用启用/重置密码），
 * 防止有人乱设置；所有操作记审计日志。
 */
export function MembersPanel() {
  const qc = useQueryClient();
  const query = useQuery({ queryKey: ['admin-users'], queryFn: adminListUsers });
  const [editing, setEditing] = useState<number | null>(null);

  const list = query.data ?? [];

  return (
    <div className="space-y-4">
      {query.isLoading && <Muted>加载中…</Muted>}

      <Card>
        <div className="mb-2 text-sm font-medium">
          成员列表
          <span className="ml-2 text-xs text-muted-foreground">{list.length} 人</span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr
                className="text-left text-[11px] text-muted-foreground"
                style={{ borderBottom: '1px solid var(--border)' }}
              >
                <th className="px-2 py-2 font-normal">学号</th>
                <th className="px-2 py-2 font-normal">站内名称</th>
                <th className="px-2 py-2 font-normal">真实姓名</th>
                <th className="px-2 py-2 font-normal">班级</th>
                <th className="px-2 py-2 font-normal">CF</th>
                <th className="px-2 py-2 font-normal">AtCoder</th>
                <th className="px-2 py-2 font-normal">洛谷</th>
                <th className="px-2 py-2 font-normal">状态</th>
                <th className="px-2 py-2 font-normal">操作</th>
              </tr>
            </thead>
            <tbody>
              {list.map((u) => (
                <tr key={u.id} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td className="px-2 py-2 font-mono text-xs">{u.username}</td>
                  <td className="px-2 py-2">{u.nickname}</td>
                  <td className="px-2 py-2 text-xs text-muted-foreground">{u.realName}</td>
                  <td className="px-2 py-2 text-xs text-muted-foreground">{u.className}</td>
                  <td className="px-2 py-2 font-mono text-xs">{u.cfHandle || '—'}</td>
                  <td className="px-2 py-2 font-mono text-xs">{u.atcoderHandle || '—'}</td>
                  <td className="px-2 py-2 font-mono text-xs">{u.luoguHandle || '—'}</td>
                  <td className="px-2 py-2">
                    {u.status === 'ACTIVE' ? (
                      <Badge tone="success">正常</Badge>
                    ) : (
                      <Badge tone="destructive">已停用</Badge>
                    )}
                  </td>
                  <td className="px-2 py-2">
                    <Button size="sm" onClick={() => setEditing(editing === u.id ? null : u.id)}>
                      {editing === u.id ? '收起' : '编辑'}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {editing !== null && (
        <MemberEditCard
          user={list.find((u) => u.id === editing)!}
          onDone={() => {
            setEditing(null);
            void qc.invalidateQueries({ queryKey: ['admin-users'] });
          }}
        />
      )}
    </div>
  );
}

function MemberEditCard({ user, onDone }: { user: AdminUserRow; onDone: () => void }) {
  const qc = useQueryClient();
  const [nickname, setNickname] = useState(user.nickname);
  const [realName, setRealName] = useState(user.realName);
  const [className, setClassName] = useState(user.className);
  const [cfHandle, setCfHandle] = useState(user.cfHandle);
  const [atcoderHandle, setAtcoderHandle] = useState(user.atcoderHandle);
  const [luoguHandle, setLuoguHandle] = useState(user.luoguHandle);
  const [newPassword, setNewPassword] = useState('');
  const [status, setStatus] = useState(user.status);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const save = useMutation({
    mutationFn: () =>
      adminUpdateUser(user.id, {
        nickname,
        realName,
        className,
        status,
        cfHandle,
        atcoderHandle,
        luoguHandle,
        newPassword: newPassword || undefined,
      }),
    onSuccess: () => {
      setError(null);
      setSaved(true);
      setNewPassword('');
      void qc.invalidateQueries({ queryKey: ['admin-users'] });
      setTimeout(onDone, 800);
    },
    onError: (e: Error) => {
      setSaved(false);
      setError(e.message);
    },
  });

  return (
    <Card>
      <div className="mb-3 text-sm font-medium">
        编辑成员：{user.username}
        {user.role === 'SITE_OWNER' && <Badge tone="primary">站主</Badge>}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div>
          <div className="mb-1 text-xs text-muted-foreground">站内名称</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            maxLength={32}
          />
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">真实姓名</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={realName}
            onChange={(e) => setRealName(e.target.value)}
            maxLength={32}
          />
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">班级</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={className}
            onChange={(e) => setClassName(e.target.value)}
            maxLength={64}
          />
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">账号状态</div>
          <select
            className="w-full rounded-[var(--radius)] border bg-transparent px-2 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)', color: 'var(--foreground)', backgroundColor: 'var(--card)' }}
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            <option value="ACTIVE">正常</option>
            <option value="DISABLED">停用（禁止登录）</option>
          </select>
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">Codeforces</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={cfHandle}
            onChange={(e) => setCfHandle(e.target.value)}
          />
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">AtCoder</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={atcoderHandle}
            onChange={(e) => setAtcoderHandle(e.target.value)}
          />
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">洛谷</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            value={luoguHandle}
            onChange={(e) => setLuoguHandle(e.target.value)}
          />
        </div>
        <div>
          <div className="mb-1 text-xs text-muted-foreground">重置密码（留空不改）</div>
          <input
            className="w-full rounded-[var(--radius)] border bg-transparent px-2.5 py-1.5 text-sm outline-none"
            style={{ borderColor: 'var(--input)' }}
            type="text"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            maxLength={64}
            placeholder="8 位以上"
          />
        </div>
      </div>

      {error && <div className="mt-2"><Notice tone="danger">{error}</Notice></div>}
      {saved && <div className="mt-2"><Notice>已保存（所有修改记入审计日志）</Notice></div>}

      <Button
        size="sm"
        variant="primary"
        className="mt-3"
        disabled={save.isPending}
        onClick={() => {
          setError(null);
          save.mutate();
        }}
      >
        <Save className="h-3.5 w-3.5" />
        {save.isPending ? '保存中…' : '保存修改'}
      </Button>
    </Card>
  );
}

function Muted({ children }: { children: React.ReactNode }) {
  return <div className="py-8 text-center text-sm text-muted-foreground">{children}</div>;
}
