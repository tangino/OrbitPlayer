/**
 * Orbit Player - Cloudflare Worker & D1 Backend API
 */

export interface Env {
	orbit_db: D1Database;
	API_SECRET?: string;
}

// 统一 JSON 响应辅助函数
function jsonResponse(data: unknown, status = 200): Response {
	return new Response(JSON.stringify(data), {
		status,
		headers: {
			"Content-Type": "application/json; charset=utf-8",
			"Access-Control-Allow-Origin": "*",
			"Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
			"Access-Control-Allow-Headers": "Content-Type, Authorization",
		},
	});
}

// 密码 SHA-256 哈希计算辅助函数
async function hashPassword(password: string): Promise<string> {
	const msgUint8 = new TextEncoder().encode(password + "_orbit_salt_2026");
	const hashBuffer = await crypto.subtle.digest("SHA-256", msgUint8);
	const hashArray = Array.from(new Uint8Array(hashBuffer));
	return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

// 校验或自动注册用户账号
async function verifyOrCreateUser(env: Env, username: string, password?: string): Promise<{ success: boolean; message: string; isNew?: boolean }> {
	const cleanUsername = username.trim();
	if (!cleanUsername || cleanUsername.length < 2) {
		return { success: false, message: "账号名称不能少于 2 个字符" };
	}

	const existingUser = await env.orbit_db
		.prepare("SELECT * FROM users WHERE username = ?")
		.bind(cleanUsername)
		.first();

	if (!password) {
		// 如果未提供密码且用户存在，允许纯旧版 userId 同步（保持兼容）
		return { success: true, message: "验证通过", isNew: !existingUser };
	}

	const pwdHash = await hashPassword(password);
	const now = Date.now();

	if (!existingUser) {
		// 自动注册新用户
		await env.orbit_db
			.prepare("INSERT INTO users (username, password_hash, created_at, updated_at) VALUES (?, ?, ?, ?)")
			.bind(cleanUsername, pwdHash, now, now)
			.run();
		return { success: true, message: "新账号创建并登录成功", isNew: true };
	}

	// 校验现有用户密码
	if (existingUser.password_hash !== pwdHash) {
		return { success: false, message: "密码错误，请检查输入的密码是否正确" };
	}

	return { success: true, message: "登录成功", isNew: false };
}

export default {
	async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
		const url = new URL(request.url);
		const path = url.pathname;
		const method = request.method;

		// 处理跨域 OPTIONS 预检请求
		if (method === "OPTIONS") {
			return new Response(null, {
				status: 204,
				headers: {
					"Access-Control-Allow-Origin": "*",
					"Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
					"Access-Control-Allow-Headers": "Content-Type, Authorization",
					"Access-Control-Max-Age": "86400",
				},
			});
		}

		try {
			// 0. 健康检查接口
			if (path === "/" || path === "/api/health") {
				return jsonResponse({
					success: true,
					name: "Orbit Player Cloud API",
					version: "1.1.0",
					database: "Cloudflare D1 Ready",
					timestamp: Date.now(),
				});
			}

			// ==============================
			// 0. 用户账号与认证接口 (User Auth)
			// ==============================
			if (path === "/api/user/auth" && method === "POST") {
				const body = (await request.json()) as any;
				const { username, password } = body;

				if (!username || !password) {
					return jsonResponse({ success: false, message: "请输入账号和密码" }, 400);
				}

				const authResult = await verifyOrCreateUser(env, username, password);
				if (!authResult.success) {
					return jsonResponse({ success: false, message: authResult.message }, 401);
				}

				return jsonResponse({
					success: true,
					username: username.trim(),
					isNew: authResult.isNew,
					message: authResult.message,
				});
			}

			// ==============================
			// 1. 音源脚本管理接口 (Source Scripts)
			// ==============================

			// 1.1 获取全部已启用的音源订阅列表 [GET /api/sources]
			if (path === "/api/sources" && method === "GET") {
				const { results } = await env.orbit_db
					.prepare("SELECT * FROM source_scripts WHERE is_active = 1 ORDER BY updated_at DESC")
					.all();
				return jsonResponse({ success: true, count: results.length, data: results });
			}

			// 1.2 发布/更新音源脚本 [POST /api/sources]
			if (path === "/api/sources" && method === "POST") {
				const body = (await request.json()) as any;
				const { id, name, version, author, description, scriptUrl, isActive = 1 } = body;

				if (!id || !name || !scriptUrl) {
					return jsonResponse({ success: false, message: "缺少必要字段 (id, name, scriptUrl)" }, 400);
				}

				const now = Date.now();
				await env.orbit_db
					.prepare(
						`
					INSERT INTO source_scripts (id, name, version, author, description, script_url, is_active, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
					ON CONFLICT(id) DO UPDATE SET
						name = excluded.name,
						version = excluded.version,
						author = excluded.author,
						description = excluded.description,
						script_url = excluded.script_url,
						is_active = excluded.is_active,
						updated_at = excluded.updated_at
				`
					)
					.bind(id, name, version || "1.0.0", author || "社区", description || "", scriptUrl, isActive, now, now)
					.run();

				return jsonResponse({ success: true, message: "音源脚本保存成功" });
			}

			// 1.3 用户音源全量云端备份 [POST /api/user/sources/backup]
			if (path === "/api/user/sources/backup" && method === "POST") {
				const body = (await request.json()) as any;
				const username = (body.username || body.userId || "").trim();
				const { password, sourcesJson, sourceCount } = body;

				if (!username || !sourcesJson) {
					return jsonResponse({ success: false, message: "缺少必要字段 (username, sourcesJson)" }, 400);
				}

				// 账号密码校验
				if (password) {
					const authResult = await verifyOrCreateUser(env, username, password);
					if (!authResult.success) {
						return jsonResponse({ success: false, message: authResult.message }, 401);
					}
				}

				const now = Date.now();
				const count = typeof sourceCount === "number" ? sourceCount : (JSON.parse(sourcesJson || "[]") as any[]).length;

				await env.orbit_db
					.prepare(
						`
					INSERT INTO user_source_backups (user_id, sources_json, source_count, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?)
					ON CONFLICT(user_id) DO UPDATE SET
						sources_json = excluded.sources_json,
						source_count = excluded.source_count,
						updated_at = excluded.updated_at
				`
					)
					.bind(username, sourcesJson, count, now, now)
					.run();

				return jsonResponse({
					success: true,
					message: "音源已成功备份至云端账号",
					username,
					count,
					updatedAt: now,
				});
			}

			// 1.4 用户音源云端恢复与拉取 [GET /api/user/sources/restore?username=xxx&password=yyy] 或 POST
			if (path === "/api/user/sources/restore") {
				let username = "";
				let password = "";

				if (method === "POST") {
					const body = (await request.json()) as any;
					username = (body.username || body.userId || "").trim();
					password = body.password || "";
				} else {
					username = (url.searchParams.get("username") || url.searchParams.get("userId") || "").trim();
					password = url.searchParams.get("password") || "";
				}

				if (!username) {
					return jsonResponse({ success: false, message: "缺少 username 账号参数" }, 400);
				}

				// 如果提供了密码，进行密码校验
				if (password) {
					const authResult = await verifyOrCreateUser(env, username, password);
					if (!authResult.success) {
						return jsonResponse({ success: false, message: authResult.message }, 401);
					}
				}

				const record = await env.orbit_db
					.prepare("SELECT * FROM user_source_backups WHERE user_id = ?")
					.bind(username)
					.first();

				if (!record) {
					return jsonResponse({ success: false, message: `账号 [${username}] 暂无云端音源备份数据，请先在已配置音源的设备上点击上传备份` }, 404);
				}

				return jsonResponse({
					success: true,
					username,
					data: record,
				});
			}

			// 1.5 删除用户云端音源备份 [POST /api/user/sources/delete]
			if (path === "/api/user/sources/delete" && (method === "POST" || method === "DELETE")) {
				let username = "";
				let password = "";

				if (method === "POST") {
					const body = (await request.json()) as any;
					username = (body.username || body.userId || "").trim();
					password = body.password || "";
				} else {
					username = (url.searchParams.get("username") || url.searchParams.get("userId") || "").trim();
					password = url.searchParams.get("password") || "";
				}

				if (!username) {
					return jsonResponse({ success: false, message: "缺少 username 账号参数" }, 400);
				}

				if (password) {
					const authResult = await verifyOrCreateUser(env, username, password);
					if (!authResult.success) {
						return jsonResponse({ success: false, message: authResult.message }, 401);
					}
				}

				await env.orbit_db
					.prepare("DELETE FROM user_source_backups WHERE user_id = ?")
					.bind(username)
					.run();

				return jsonResponse({
					success: true,
					message: "云端音源备份已成功删除",
					username,
				});
			}

			// ==============================
			// 2. 歌单多端云同步接口 (Playlists Sync)
			// ==============================

			// 2.0 用户歌单多端全量/选择性云备份 [POST /api/user/playlists/backup]
			if (path === "/api/user/playlists/backup" && method === "POST") {
				const body = (await request.json()) as any;
				const username = (body.username || body.userId || "").trim();
				const { password, playlistsJson, onlineFavoritesJson, groupsJson, playlistCount, mode = "merge" } = body;

				if (!username || !playlistsJson) {
					return jsonResponse({ success: false, message: "缺少必要字段 (username, playlistsJson)" }, 400);
				}

				if (password) {
					const authResult = await verifyOrCreateUser(env, username, password);
					if (!authResult.success) {
						return jsonResponse({ success: false, message: authResult.message }, 401);
					}
				}

				const now = Date.now();
				let finalPlaylists = JSON.parse(playlistsJson || "[]") as any[];
				let finalOnlineFavs = JSON.parse(onlineFavoritesJson || "[]") as any[];
				let finalGroups = JSON.parse(groupsJson || "[]") as string[];

				// 若为增量合并模式，先尝试拉取云端已有备份并进行智能合并
				if (mode === "merge") {
					const existingRecord = await env.orbit_db
						.prepare("SELECT * FROM user_playlist_backups WHERE user_id = ?")
						.bind(username)
						.first();

					if (existingRecord) {
						try {
							const existingPlaylists = JSON.parse((existingRecord.playlists_json as string) || "[]") as any[];
							const existingOnlineFavs = JSON.parse((existingRecord.online_favorites_json as string) || "[]") as any[];
							const existingGroups = JSON.parse((existingRecord.groups_json as string) || "[]") as string[];

							// 合并本地歌单 (同名歌单合并曲目并去重)
							const mergedLocalMap = new Map<string, any>();
							for (const p of existingPlaylists) {
								if (p.name) mergedLocalMap.set(p.name, p);
							}
							for (const p of finalPlaylists) {
								if (!p.name) continue;
								if (mergedLocalMap.has(p.name)) {
									const existingP = mergedLocalMap.get(p.name);
									const existingSongs = existingP.songs || [];
									const newSongs = p.songs || [];
									// 根据 path 或 title+artist 去重
									const songKey = (s: any) => s.path || `${s.title}__${s.artist}`;
									const songMap = new Map<string, any>();
									for (const s of existingSongs) songMap.set(songKey(s), s);
									for (const s of newSongs) songMap.set(songKey(s), s);
									mergedLocalMap.set(p.name, {
										...existingP,
										groupName: p.groupName || existingP.groupName || "默认",
										songs: Array.from(songMap.values()),
										createdAt: p.createdAt || existingP.createdAt || now,
									});
								} else {
									mergedLocalMap.set(p.name, p);
								}
							}
							finalPlaylists = Array.from(mergedLocalMap.values());

							// 合并在线收藏 (根据 id 去重)
							const onlineMap = new Map<string, any>();
							for (const op of existingOnlineFavs) {
								if (op.id) onlineMap.set(op.id, op);
							}
							for (const op of finalOnlineFavs) {
								if (op.id) onlineMap.set(op.id, op);
							}
							finalOnlineFavs = Array.from(onlineMap.values());

							// 合并分组
							const groupSet = new Set<string>([...existingGroups, ...finalGroups]);
							finalGroups = Array.from(groupSet).filter((g) => g && g !== "默认");
						} catch (_e) {
							// 解析异常时保持当前上传数据
						}
					}
				}

				const count = finalPlaylists.length + finalOnlineFavs.length;
				const savedPlaylistsJson = JSON.stringify(finalPlaylists);
				const savedOnlineFavsJson = JSON.stringify(finalOnlineFavs);
				const savedGroupsJson = JSON.stringify(finalGroups);

				await env.orbit_db
					.prepare(
						`
					INSERT INTO user_playlist_backups (user_id, playlists_json, online_favorites_json, groups_json, playlist_count, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					ON CONFLICT(user_id) DO UPDATE SET
						playlists_json = excluded.playlists_json,
						online_favorites_json = excluded.online_favorites_json,
						groups_json = excluded.groups_json,
						playlist_count = excluded.playlist_count,
						updated_at = excluded.updated_at
				`
					)
					.bind(username, savedPlaylistsJson, savedOnlineFavsJson, savedGroupsJson, count, now, now)
					.run();

				return jsonResponse({
					success: true,
					message: mode === "overwrite" ? "歌单已全量覆盖备份至云端" : "歌单已增量合并备份至云端",
					mode,
					username,
					count,
					updatedAt: now,
				});
			}

			// 2.0.1 用户歌单多端恢复与拉取 [GET/POST /api/user/playlists/restore]
			if (path === "/api/user/playlists/restore") {
				let username = "";
				let password = "";

				if (method === "POST") {
					const body = (await request.json()) as any;
					username = (body.username || body.userId || "").trim();
					password = body.password || "";
				} else {
					username = (url.searchParams.get("username") || url.searchParams.get("userId") || "").trim();
					password = url.searchParams.get("password") || "";
				}

				if (!username) {
					return jsonResponse({ success: false, message: "缺少 username 账号参数" }, 400);
				}

				if (password) {
					const authResult = await verifyOrCreateUser(env, username, password);
					if (!authResult.success) {
						return jsonResponse({ success: false, message: authResult.message }, 401);
					}
				}

				const record = await env.orbit_db
					.prepare("SELECT * FROM user_playlist_backups WHERE user_id = ?")
					.bind(username)
					.first();

				if (!record) {
					return jsonResponse({ success: false, message: `账号 [${username}] 暂无云端歌单备份数据` }, 404);
				}

				return jsonResponse({
					success: true,
					username,
					data: record,
				});
			}

			// 2.0.2 用户云端歌单备份删除/清空 [POST /api/user/playlists/delete]
			if (path === "/api/user/playlists/delete" && (method === "POST" || method === "DELETE")) {
				const body = (await request.json().catch(() => ({}))) as any;
				const username = (body.username || url.searchParams.get("username") || "").trim();
				const password = body.password || url.searchParams.get("password") || "";
				const deleteAll = body.deleteAll === true;
				const localPlaylistNames: string[] = body.localPlaylistNames || [];
				const onlinePlaylistIds: string[] = body.onlinePlaylistIds || [];

				if (!username) {
					return jsonResponse({ success: false, message: "缺少 username 账号参数" }, 400);
				}

				if (password) {
					const authResult = await verifyOrCreateUser(env, username, password);
					if (!authResult.success) {
						return jsonResponse({ success: false, message: authResult.message }, 401);
					}
				}

				if (deleteAll || (localPlaylistNames.length === 0 && onlinePlaylistIds.length === 0)) {
					// 全量清空该用户的云端歌单备份
					await env.orbit_db
						.prepare("DELETE FROM user_playlist_backups WHERE user_id = ?")
						.bind(username)
						.run();
					return jsonResponse({ success: true, message: "已清空云端所有歌单备份数据", count: 0 });
				}

				// 选择性删除特定歌单
				const record = await env.orbit_db
					.prepare("SELECT * FROM user_playlist_backups WHERE user_id = ?")
					.bind(username)
					.first();

				if (!record) {
					return jsonResponse({ success: true, message: "云端歌单已被删除", count: 0 });
				}

				let existingPlaylists = JSON.parse((record.playlists_json as string) || "[]") as any[];
				let existingOnlineFavs = JSON.parse((record.online_favorites_json as string) || "[]") as any[];

				const deleteLocalSet = new Set(localPlaylistNames);
				const deleteOnlineSet = new Set(onlinePlaylistIds);

				existingPlaylists = existingPlaylists.filter((p) => !deleteLocalSet.has(p.name));
				existingOnlineFavs = existingOnlineFavs.filter((op) => !deleteOnlineSet.has(op.id) && !deleteOnlineSet.has(`${op.platform}_${op.id}`));

				const remainingCount = existingPlaylists.length + existingOnlineFavs.length;

				if (remainingCount === 0) {
					await env.orbit_db
						.prepare("DELETE FROM user_playlist_backups WHERE user_id = ?")
						.bind(username)
						.run();
					return jsonResponse({ success: true, message: "所选歌单已删除，云端歌单已清空", count: 0 });
				}

				const now = Date.now();
				await env.orbit_db
					.prepare(
						`
					UPDATE user_playlist_backups SET
						playlists_json = ?,
						online_favorites_json = ?,
						playlist_count = ?,
						updated_at = ?
					WHERE user_id = ?
				`
					)
					.bind(JSON.stringify(existingPlaylists), JSON.stringify(existingOnlineFavs), remainingCount, now, username)
					.run();

				return jsonResponse({
					success: true,
					message: `已删除所选歌单，云端剩余 ${remainingCount} 个歌单`,
					count: remainingCount,
				});
			}

			// 2.0.3 清空用户全部云端数据（音源+歌单+偏好设置） [POST /api/user/data/clear]
			if (path === "/api/user/data/clear" && method === "POST") {
				const body = (await request.json()) as any;
				const username = (body.username || "").trim();
				const password = body.password || "";

				if (!username) {
					return jsonResponse({ success: false, message: "缺少 username 账号参数" }, 400);
				}

				if (password) {
					const authResult = await verifyOrCreateUser(env, username, password);
					if (!authResult.success) {
						return jsonResponse({ success: false, message: authResult.message }, 401);
					}
				}

				await env.orbit_db.batch([
					env.orbit_db.prepare("DELETE FROM user_source_backups WHERE user_id = ?").bind(username),
					env.orbit_db.prepare("DELETE FROM user_playlist_backups WHERE user_id = ?").bind(username),
					env.orbit_db.prepare("DELETE FROM user_playlists WHERE user_id = ?").bind(username),
					env.orbit_db.prepare("DELETE FROM user_settings WHERE user_id = ?").bind(username),
				]);

				return jsonResponse({
					success: true,
					message: "已完全清除该账号下的所有云端数据（音源、歌单与偏好设置）",
				});
			}

			// 2.1 同步/上传单张歌单 [POST /api/playlist/sync]
			if (path === "/api/playlist/sync" && method === "POST") {
				const body = (await request.json()) as any;
				const { id, userId, playlistName, songListJson, songCount } = body;

				if (!id || !userId || !playlistName) {
					return jsonResponse({ success: false, message: "缺少必要参数 (id, userId, playlistName)" }, 400);
				}

				const now = Date.now();
				const count = typeof songCount === "number" ? songCount : (JSON.parse(songListJson || "[]") as any[]).length;

				await env.orbit_db
					.prepare(
						`
					INSERT INTO user_playlists (id, user_id, playlist_name, song_list_json, song_count, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					ON CONFLICT(id) DO UPDATE SET
						playlist_name = excluded.playlist_name,
						song_list_json = excluded.song_list_json,
						song_count = excluded.song_count,
						updated_at = excluded.updated_at
				`
					)
					.bind(id, userId, playlistName, songListJson || "[]", count, now, now)
					.run();

				return jsonResponse({ success: true, message: "歌单同步成功", id, updatedAt: now });
			}

			// 2.2 拉取用户的所有云端歌单 [GET /api/playlist/pull?userId=xxx]
			if (path === "/api/playlist/pull" && method === "GET") {
				const userId = url.searchParams.get("userId");
				if (!userId) {
					return jsonResponse({ success: false, message: "缺少 userId 参数" }, 400);
				}

				const { results } = await env.orbit_db
					.prepare("SELECT * FROM user_playlists WHERE user_id = ? ORDER BY updated_at DESC")
					.bind(userId)
					.all();

				return jsonResponse({ success: true, count: results.length, data: results });
			}

			// 2.3 删除云端歌单 [DELETE /api/playlist/delete?id=xxx&userId=xxx]
			if (path === "/api/playlist/delete" && (method === "DELETE" || method === "POST")) {
				const playlistId = url.searchParams.get("id");
				const userId = url.searchParams.get("userId");

				if (!playlistId || !userId) {
					return jsonResponse({ success: false, message: "缺少 id 或 userId 参数" }, 400);
				}

				await env.orbit_db
					.prepare("DELETE FROM user_playlists WHERE id = ? AND user_id = ?")
					.bind(playlistId, userId)
					.run();

				return jsonResponse({ success: true, message: "歌单已从云端删除" });
			}

			// ==============================
			// 3. 用户偏好与均衡器配置同步 (User Settings)
			// ==============================

			// 3.1 上传配置 [POST /api/settings/sync]
			if (path === "/api/settings/sync" && method === "POST") {
				const body = (await request.json()) as any;
				const { userId, preferredQuality, equalizerPreset, settingsJson } = body;

				if (!userId) {
					return jsonResponse({ success: false, message: "缺少 userId 参数" }, 400);
				}

				const now = Date.now();
				await env.orbit_db
					.prepare(
						`
					INSERT INTO user_settings (user_id, preferred_quality, equalizer_preset, settings_json, updated_at)
					VALUES (?, ?, ?, ?, ?)
					ON CONFLICT(user_id) DO UPDATE SET
						preferred_quality = excluded.preferred_quality,
						equalizer_preset = excluded.equalizer_preset,
						settings_json = excluded.settings_json,
						updated_at = excluded.updated_at
				`
					)
					.bind(userId, preferredQuality || "flac", equalizerPreset || "Default", settingsJson || "{}", now)
					.run();

				return jsonResponse({ success: true, message: "设置配置同步成功", updatedAt: now });
			}

			// 3.2 拉取配置 [GET /api/settings/pull?userId=xxx]
			if (path === "/api/settings/pull" && method === "GET") {
				const userId = url.searchParams.get("userId");
				if (!userId) {
					return jsonResponse({ success: false, message: "缺少 userId 参数" }, 400);
				}

				const result = await env.orbit_db
					.prepare("SELECT * FROM user_settings WHERE user_id = ?")
					.bind(userId)
					.first();

				return jsonResponse({ success: true, data: result || null });
			}

			// 404 路由未找到
			return jsonResponse({ success: false, message: "Not Found: 接口不存在" }, 404);
		} catch (error: any) {
			return jsonResponse({ success: false, error: error?.message || "服务器内部错误" }, 500);
		}
	},
} satisfies ExportedHandler<Env>;
