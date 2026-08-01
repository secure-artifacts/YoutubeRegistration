document.addEventListener('DOMContentLoaded', function() {
  const webAppUrlInput = document.getElementById('webAppUrl');
  const saveUrlBtn = document.getElementById('saveUrlBtn');
  const urlStatus = document.getElementById('urlStatus');
  
  const titleInput = document.getElementById('title');
  const urlInput = document.getElementById('url');
  const regStatusSpan = document.getElementById('regStatus'); // 登记状态标签

  const durationField = document.getElementById('durationField'); // 时长显示区域
  const durationInput = document.getElementById('duration');
  const durationBadge = document.getElementById('durationBadge');

  const modeRadios = document.querySelectorAll('input[name="workMode"]');
  const dakaFields = document.getElementById('dakaFields');
  const waiwenFields = document.getElementById('waiwenFields');
  
  const categorySelect = document.getElementById('category');
  const noteInput = document.getElementById('note');
  const mobileClicksInput = document.getElementById('mobileClicks');
  
  const submitBtn = document.getElementById('submitBtn');
  const modifyBtn = document.getElementById('modifyBtn'); // 新增：修改备注按钮
  const statusDiv = document.getElementById('status');

  let videoDuration = 0; // 当前视频时长（秒），0 表示未获取到

  // 获取今天的日期字符串 (格式: "2023-10-25")
  function getTodayString() {
    const d = new Date();
    return d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate();
  }

  // 格式化秒数为可读字符串（如 "1小时23分45秒" 或 "45分30秒"）
  function formatDuration(seconds) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    if (h > 0) return `${h}小时${m}分${s}秒`;
    return `${m}分${s}秒`;
  }

  // 检查当前链接是否在今天已经登记过
  function checkRegistrationStatus(currentUrl) {
    chrome.storage.local.get(['dailyHistory'], function(result) {
      const history = result.dailyHistory || {};
      const today = getTodayString();
      const todaysRecords = history[today] || [];
      
      if (todaysRecords.includes(currentUrl)) {
        regStatusSpan.textContent = "已登记";
        regStatusSpan.className = "badge registered";
        submitBtn.innerText = "重新登记 (覆盖)";
      } else {
        regStatusSpan.textContent = "未登记";
        regStatusSpan.className = "badge unregistered";
        submitBtn.innerText = "登记";
      }
    });
  }

  // 将成功登记的链接保存到本地 (只保留当天的，自动清理历史数据)
  function saveRegistrationToLocal(currentUrl) {
    chrome.storage.local.get(['dailyHistory'], function(result) {
      const today = getTodayString();
      // 故意创建一个新的空对象，只保存今天的记录，抛弃以前的，防止存储空间爆满
      const newHistory = {};
      newHistory[today] = (result.dailyHistory && result.dailyHistory[today]) ? result.dailyHistory[today] : [];
      
      if (!newHistory[today].includes(currentUrl)) {
        newHistory[today].push(currentUrl);
      }
      
      chrome.storage.local.set({ 'dailyHistory': newHistory }, function() {
        // 存储成功后，立刻更新界面状态
        regStatusSpan.textContent = "已登记";
        regStatusSpan.className = "badge registered";
      });
    });
  }

  // ================= 模式切换逻辑 =================
  function updateModeUI() {
    const selectedMode = document.querySelector('input[name="workMode"]:checked').value;
    if (selectedMode === "外文点击") {
      dakaFields.style.display = 'none';
      waiwenFields.style.display = 'block';
      modifyBtn.style.display = 'none'; // 外文模式隐藏修改按钮
      document.body.classList.add('waiwen-mode');
    } else {
      dakaFields.style.display = 'block';
      waiwenFields.style.display = 'none';
      modifyBtn.style.display = ''; // 视频打卡模式显示修改按钮
      document.body.classList.remove('waiwen-mode');
    }
  }

  modeRadios.forEach(radio => {
    radio.addEventListener('change', updateModeUI);
  });
  updateModeUI();

  // ================= 设置区域逻辑 =================
  chrome.storage.local.get(['savedUrl'], function(result) {
    if (result.savedUrl) {
      webAppUrlInput.value = result.savedUrl;
    }
  });

  saveUrlBtn.addEventListener('click', function() {
    const urlValue = webAppUrlInput.value.trim();
    if (!urlValue) {
      urlStatus.innerHTML = "❌ 请先输入链接";
      urlStatus.className = "error";
      return;
    }
    chrome.storage.local.set({ 'savedUrl': urlValue }, function() {
      urlStatus.innerHTML = "✅ 保存成功！";
      urlStatus.className = "success";
      setTimeout(() => urlStatus.innerHTML = "", 2000); 
    });
  });

  // ================= 界面显示逻辑 =================
  function updateSelectColor() {
    categorySelect.style.color = categorySelect.options[categorySelect.selectedIndex].style.color;
  }
  categorySelect.addEventListener('change', updateSelectColor);
  updateSelectColor();

  // ================= 抓取网页逻辑 & 检查记录 & 获取视频时长 =================
  chrome.tabs.query({ active: true, currentWindow: true }, function(tabs) {
    let currentTab = tabs[0];
    if (currentTab) {
      titleInput.value = currentTab.title;
      urlInput.value = currentTab.url;
      // 拿到 URL 后立刻检查是否登记过
      checkRegistrationStatus(currentTab.url);

      // 如果是 YouTube 视频页，尝试通过 scripting API 读取 <video> 时长
      if (currentTab.url && currentTab.url.includes('youtube.com/watch')) {
        chrome.scripting.executeScript({
          target: { tabId: currentTab.id },
          func: () => {
            const video = document.querySelector('video');
            return video ? video.duration : null;
          }
        }, (results) => {
          // 权限不足或注入失败时静默忽略（不影响其他功能）
          if (chrome.runtime.lastError) return;
          if (results && results[0] && results[0].result != null && isFinite(results[0].result)) {
            videoDuration = results[0].result;
            durationInput.value = formatDuration(videoDuration);
            durationField.style.display = 'block';

            const isLong = videoDuration > 40 * 60; // 是否超过 40 分钟
            durationBadge.textContent = isLong ? '⚠️ 超40分钟（将登记2条）' : '✅ 正常';
            durationBadge.className = isLong ? 'badge registered' : 'badge unregistered';
          }
        });
      }
    }
  });

  // ================= 单次 POST 封装（返回 Promise）=================
  async function postData(targetUrl, formData) {
    const response = await fetch(targetUrl, {
      method: 'POST',
      body: formData,
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    });
    if (!response.ok) throw new Error('网络错误');
    return response;
  }

  // ================= 登记提交逻辑 =================
  submitBtn.addEventListener('click', async function() {
    const targetUrl = webAppUrlInput.value.trim();
    if (!targetUrl || !targetUrl.startsWith("http")) {
      statusDiv.innerHTML = "❌ 请先配置 Web App URL！";
      statusDiv.className = "error";
      return;
    }

    const selectedMode = document.querySelector('input[name="workMode"]:checked').value;
    // 视频打卡模式下，超过 40 分钟需要登记两条
    const needDouble = (selectedMode === "视频打卡" && videoDuration > 40 * 60);

    // 禁用按钮防连点
    submitBtn.disabled = true;
    modifyBtn.disabled = true;
    statusDiv.innerHTML = "";
    submitBtn.innerText = needDouble ? "⏳ 发送中 (1/2)..." : "⏳ 正在发送...";

    const formData = new URLSearchParams();
    formData.append('action', 'register');
    formData.append('title', titleInput.value);
    formData.append('url', urlInput.value);
    formData.append('mode', selectedMode);

    if (selectedMode === "视频打卡") {
      formData.append('option', categorySelect.value);
      formData.append('note', noteInput.value);
    } else if (selectedMode === "外文点击") {
      formData.append('mobileClicks', mobileClicksInput.value);
    }

    try {
      // 第一条
      await postData(targetUrl, formData);

      // 超过 40 分钟：发第二条（内容相同）
      if (needDouble) {
        submitBtn.innerText = "⏳ 发送中 (2/2)...";
        await postData(targetUrl, formData);
        statusDiv.innerHTML = "✅ 登记成功！（超40分钟，已自动登记 2 条）";
      } else {
        statusDiv.innerHTML = "✅ 登记成功！";
      }

      statusDiv.className = "success";
      submitBtn.innerText = "✅ 已完成";
      noteInput.value = "";
      mobileClicksInput.value = "";

      // ★★★ 核心：登记成功后，将此 URL 写入本地记录 ★★★
      saveRegistrationToLocal(urlInput.value);

    } catch (error) {
      statusDiv.innerHTML = "❌ 发送失败，请检查网络";
      statusDiv.className = "error";
      submitBtn.disabled = false;
      modifyBtn.disabled = false;
      submitBtn.innerText = "登记"; // 失败则恢复原始文字
    }
  });

  // ================= 修改备注逻辑 =================
  modifyBtn.addEventListener('click', async function() {
    const targetUrl = webAppUrlInput.value.trim();
    if (!targetUrl || !targetUrl.startsWith("http")) {
      statusDiv.innerHTML = "❌ 请先配置 Web App URL！";
      statusDiv.className = "error";
      return;
    }

    // 禁用按钮防连点
    modifyBtn.disabled = true;
    submitBtn.disabled = true;
    modifyBtn.innerText = "⏳ 修改中...";
    statusDiv.innerHTML = "";

    // 发送：action=modify + 当前链接 + 日期 + 新备注
    const formData = new URLSearchParams();
    formData.append('action', 'modify');
    formData.append('url', urlInput.value);
    formData.append('date', getTodayString());
    formData.append('note', noteInput.value.trim());

    try {
      await postData(targetUrl, formData);
      statusDiv.innerHTML = "✅ 备注修改成功！";
      statusDiv.className = "success";
    } catch (error) {
      statusDiv.innerHTML = "❌ 修改失败，请检查网络";
      statusDiv.className = "error";
    } finally {
      // 无论成功失败，都恢复按钮状态
      modifyBtn.disabled = false;
      submitBtn.disabled = false;
      modifyBtn.innerText = "✏️ 修改备注";
    }
  });
});