<script setup>
/*
 * 知识点地图: one row per knowledge point, one column per material of the chapter (documents and videos).
 * A cell lists where the point is taught: page numbers for a PDF, start times for a video.
 * Clicking a spot emits `jump` ({ itemId, page, startSec }) so the page can open that item there.
 */
const props = defineProps({
  map: { type: Object, required: true }, // { columns: [{ itemId, type, title }], rows: [{ kpId, name, importance, keyPointCount, spots }] }
  clickable: { type: Boolean, default: true },
})
const emit = defineEmits(['jump'])

function mmss(sec) {
  const s = Math.max(0, Math.floor(sec || 0))
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

// pages are shown compactly: 4–7、9
function pageText(spots) {
  const pages = [...new Set(spots.filter((s) => s.page != null).map((s) => s.page))].sort((a, b) => a - b)
  const parts = []
  for (let i = 0; i < pages.length; i++) {
    let j = i
    while (j + 1 < pages.length && pages[j + 1] === pages[j] + 1) j++
    parts.push(j > i ? `${pages[i]}–${pages[j]}` : `${pages[i]}`)
    i = j
  }
  return parts.length ? `第 ${parts.join('、')} 页` : ''
}

// consecutive video segments (V1-02, V1-03, V1-04) become one range "00:30–02:56"; a gap starts a new range
function times(spots) {
  const segs = spots
    .filter((s) => s.startSec != null)
    .map((s) => ({ ...s, n: Number(s.key.split('-').pop()), endSec: s.endSec ?? endFromLabel(s.label) }))
    .sort((a, b) => a.n - b.n)
  const ranges = []
  for (const s of segs) {
    const last = ranges[ranges.length - 1]
    if (last && s.n === last.n + 1) {
      last.n = s.n
      last.endSec = s.endSec ?? last.endSec
    } else {
      ranges.push({ key: s.key, n: s.n, startSec: s.startSec, endSec: s.endSec, label: s.label })
    }
  }
  return ranges
}

// older responses have no endSec; the label ends with "00:41～01:30"
function endFromLabel(label) {
  const m = /～(\d+):(\d+)$/.exec(label || '')
  return m ? Number(m[1]) * 60 + Number(m[2]) : null
}

function rangeText(r) {
  return r.endSec != null ? `${mmss(r.startSec)}–${mmss(r.endSec)}` : mmss(r.startSec)
}

function hasText(spots) {
  return spots.some((s) => s.page == null && s.startSec == null)
}

function jump(col, spot) {
  if (props.clickable) emit('jump', { itemId: col.itemId, page: spot?.page ?? null, startSec: spot?.startSec ?? null })
}
</script>

<template>
  <div class="kp-map">
    <div class="scroll">
      <table>
        <thead>
          <tr>
            <th class="kp">知识点</th>
            <th v-for="c in map.columns" :key="c.itemId">
              <el-icon><component :is="c.type === 'video' ? 'VideoPlay' : 'Document'" /></el-icon>
              {{ c.title }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in map.rows" :key="r.kpId">
            <td class="kp">
              <span class="no">{{ i + 1 }}</span>
              <strong>{{ r.name }}</strong>
              <span v-if="r.importance === '重点'" class="sw-tag orange">重点</span>
            </td>
            <td v-for="c in map.columns" :key="c.itemId">
              <template v-if="r.spots[c.itemId]">
                <button
                  v-if="pageText(r.spots[c.itemId])"
                  type="button"
                  class="spot page"
                  :disabled="!clickable"
                  @click="jump(c, r.spots[c.itemId].find((s) => s.page != null))"
                >
                  {{ pageText(r.spots[c.itemId]) }}
                </button>
                <button
                  v-for="s in times(r.spots[c.itemId])"
                  :key="s.key"
                  type="button"
                  class="spot time"
                  :disabled="!clickable"
                  :title="'点一下从 ' + mmss(s.startSec) + ' 开始看'"
                  @click="jump(c, s)"
                >
                  {{ rangeText(s) }}
                </button>
                <button
                  v-if="hasText(r.spots[c.itemId]) && !pageText(r.spots[c.itemId]) && !times(r.spots[c.itemId]).length"
                  type="button"
                  class="spot page"
                  :disabled="!clickable"
                  @click="jump(c, null)"
                >
                  正文
                </button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.scroll {
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
th,
td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  text-align: left;
  vertical-align: top;
}
th {
  font-weight: 500;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
th .el-icon {
  vertical-align: -2px;
}
td.kp {
  min-width: 190px;
}
.no {
  display: inline-block;
  width: 20px;
  color: var(--el-text-color-secondary);
}
.spot {
  display: inline-block;
  margin: 0 4px 4px 0;
  padding: 2px 8px;
  border: 0;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.spot:disabled {
  cursor: default;
}
.spot.time {
  background: #e8f1fd;
  color: #2f6fd6;
}
.spot.page {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}
.sw-tag {
  margin-left: 6px;
}
</style>
