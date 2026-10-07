<template>
  <div class="page-container">
    <el-alert type="warning" :closable="false" style="margin-bottom:12px"
      title="筛查异常二次确认：设备遮挡、佩戴不规范或环境光线导致的异常，先在原记录上发起复测，复测仍异常才把结果推给家长；复测恢复正常则记录原因并关闭本次转诊任务。" />

    <div class="filters">
      <el-select v-if="!auth.isSchool" v-model="schoolId" placeholder="全部学校" clearable
                 style="width:220px" @change="loadStats">
        <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-button @click="load" :icon="Refresh">刷新</el-button>
    </div>

    <el-row :gutter="12" style="margin-bottom:12px">
      <el-col :span="6"><el-card class="kpi-card"><div class="num" style="color:#e6a23c">{{ sm.retestTotal ?? 0 }}</div><div class="label">已复测人次</div></el-card></el-col>
      <el-col :span="6"><el-card class="kpi-card"><div class="num">{{ sm.retestRate ?? 0 }}%</div><div class="label">复测发起比例（复测/首筛异常）</div></el-card></el-col>
      <el-col :span="6"><el-card class="kpi-card"><div class="num" style="color:#67c23a">{{ sm.normalRate ?? 0 }}%</div><div class="label">复测恢复正常占比（设备/光线/组织信号）</div></el-card></el-col>
      <el-col :span="6"><el-card class="kpi-card"><div class="num" style="color:#f56c6c">{{ sm.confirmRate ?? 0 }}%</div><div class="label">复测仍异常占比（学生个体信号）</div></el-card></el-col>
    </el-row>

    <el-card>
      <el-tabs v-model="tab">
        <!-- 待复测工作台 -->
        <el-tab-pane :label="`待复测（${pending.length}）`" name="pending">
          <el-table :data="pending" stripe size="small">
            <el-table-column prop="studentName" label="姓名" width="90" />
            <el-table-column label="学校/班级" min-width="200">
              <template #default="{ row }">{{ row.schoolName }} {{ row.grade }}{{ row.className }}</template>
            </el-table-column>
            <el-table-column label="首次数据（设备/视力）" min-width="200">
              <template #default="{ row }">
                <el-tag size="small" type="danger">{{ row.originalScreen.deviceCalibNo }}</el-tag>
                <span style="margin-left:6px">{{ row.originalScreen.nakedVisionLeft }}/{{ row.originalScreen.nakedVisionRight }}
                  （{{ row.originalScreen.venue || '—' }}）</span>
              </template>
            </el-table-column>
            <el-table-column label="复测原因" min-width="180">
              <template #default="{ row }">
                <el-tag size="small" type="warning">{{ row.reasonLabel }}</el-tag>
                <div class="muted" style="font-size:12px">{{ row.reasonDetail }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="initiatedBy" label="发起人" width="170" />
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button type="primary" size="small"
                  @click="openSubmit(row)">录入复测结果</el-button>
                <el-button link size="small" @click="goDetail(row.studentId)">档案</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 异常待二次确认 -->
        <el-tab-pane label="异常待发起复测" name="abnormal">
          <el-alert type="info" :closable="false" style="margin-bottom:8px"
            title="下列学生首次筛查异常、尚在复核中。若怀疑设备遮挡/佩戴/光线等现场因素，可直接发起复测，首次结果暂不推送家长。" />
          <el-table :data="abnormalRows" stripe size="small">
            <el-table-column prop="name" label="姓名" width="90" />
            <el-table-column label="学校/班级" min-width="200">
              <template #default="{ row }">{{ row.schoolName }} {{ row.grade }}{{ row.className }}</template>
            </el-table-column>
            <el-table-column label="异常标记" min-width="150">
              <template #default="{ row }">
                <el-tag v-for="(fl,i) in row.flagLabels" :key="i" type="danger" size="small" style="margin:2px">{{ fl }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button type="warning" size="small" @click="openInit(row)">发起复测</el-button>
                <el-button size="small" @click="openReview(row)">直接复核确认</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 复测比例：按班级 -->
        <el-tab-pane label="复测比例·按班级" name="byClass">
          <el-table :data="byClass" stripe size="small">
            <el-table-column prop="schoolName" label="学校" min-width="170" />
            <el-table-column label="班级" width="130">
              <template #default="{ row }">{{ row.grade }}{{ row.className }}</template>
            </el-table-column>
            <el-table-column prop="firstAbnormal" label="首筛异常" width="90" />
            <el-table-column label="首筛异常率" width="100">
              <template #default="{ row }">{{ row.firstAbnormalRate }}%</template>
            </el-table-column>
            <el-table-column prop="retestTotal" label="复测人次" width="90" />
            <el-table-column label="复测比例" width="90">
              <template #default="{ row }">{{ row.retestRate }}%</template>
            </el-table-column>
            <el-table-column prop="retestNormal" label="复测正常" width="90" />
            <el-table-column prop="retestAbnormal" label="复测仍异常" width="90" />
            <el-table-column label="问题研判" min-width="200">
              <template #default="{ row }">
                <el-tag size="small" :type="signalType(row.signal)">{{ row.signal }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 复测比例：按设备 -->
        <el-tab-pane label="复测比例·按设备" name="byDevice">
          <el-alert type="info" :closable="false" style="margin-bottom:8px"
            title="复测结论归因到触发复测的首次筛查设备：某设备首筛异常率高、且换设备后复测多恢复正常，提示该设备需检修/校准。" />
          <el-table :data="byDevice" stripe size="small">
            <el-table-column prop="deviceCalibNo" label="设备校准编号" min-width="180" />
            <el-table-column prop="firstTotal" label="首筛人次" width="90" />
            <el-table-column prop="firstAbnormal" label="首筛异常" width="90" />
            <el-table-column label="首筛异常率" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.firstAbnormalRate >= 60 ? 'danger' : 'info'">{{ row.firstAbnormalRate }}%</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="retestTotal" label="复测人次" width="90" />
            <el-table-column label="复测正常" width="90">
              <template #default="{ row }"><span style="color:#67c23a;font-weight:600">{{ row.retestNormal }}</span></template>
            </el-table-column>
            <el-table-column prop="retestAbnormal" label="复测仍异常" width="90" />
            <el-table-column label="问题研判" min-width="200">
              <template #default="{ row }">
                <el-tag size="small" :type="signalType(row.signal)">{{ row.signal }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 复测比例：按场地 -->
        <el-tab-pane label="复测比例·按场地" name="byVenue">
          <el-alert type="info" :closable="false" style="margin-bottom:8px"
            title="按筛查场地聚合：某场地复测恢复正常占比高，多提示光线（如靠窗逆光）或现场组织（嘈杂、排队仓促）问题。" />
          <el-table :data="byVenue" stripe size="small">
            <el-table-column prop="venue" label="筛查场地" min-width="180" />
            <el-table-column prop="firstTotal" label="首筛人次" width="100" />
            <el-table-column prop="firstAbnormal" label="首筛异常" width="100" />
            <el-table-column label="首筛异常率" width="110">
              <template #default="{ row }">{{ row.firstAbnormalRate }}%</template>
            </el-table-column>
            <el-table-column prop="retestTotal" label="复测人次" width="100" />
            <el-table-column prop="retestNormal" label="复测正常" width="100" />
            <el-table-column prop="retestAbnormal" label="复测仍异常" width="100" />
            <el-table-column label="问题研判" min-width="200">
              <template #default="{ row }">
                <el-tag size="small" :type="signalType(row.signal)">{{ row.signal }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 转诊任务留痕 -->
        <el-tab-pane :label="`转诊任务留痕（${referrals.length}）`" name="referral">
          <el-alert type="success" :closable="false" style="margin-bottom:8px"
            title="家长复诊提醒（转诊任务）只关闭、不删除：复测恢复正常会关闭任务，但已发出的通知渠道/时间与关闭原因永久留痕；同一学生未结束任务唯一，杜绝重复创建。" />
          <el-table :data="referrals" stripe size="small">
            <el-table-column prop="studentName" label="姓名" width="90" />
            <el-table-column label="学校/班级" min-width="180">
              <template #default="{ row }">{{ row.schoolName }} {{ row.grade }}{{ row.className }}</template>
            </el-table-column>
            <el-table-column label="来源" width="140">
              <template #default="{ row }"><el-tag size="small" :type="row.source==='RETEST'?'warning':'info'">{{ row.sourceLabel }}</el-tag></template>
            </el-table-column>
            <el-table-column label="状态" width="150">
              <template #default="{ row }">
                <el-tag size="small" :type="referralTagType(row.status)">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="家长通知留痕" min-width="170">
              <template #default="{ row }">
                <template v-if="row.notified">
                  <el-tag size="small" type="success">已通过{{ row.notifyChannel }}发出</el-tag>
                  <div class="muted" style="font-size:12px">{{ fmt(row.notifyTime) }}</div>
                </template>
                <span v-else class="muted">尚未通知（复测确认前不推送首次结果）</span>
              </template>
            </el-table-column>
            <el-table-column prop="closeNote" label="关闭原因（复测正常等）" min-width="220" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <action-dialogs ref="dialogs" :student-id="sid" @done="load" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../api'
import { useAuthStore } from '../store/auth'
import ActionDialogs from '../components/ActionDialogs.vue'

const auth = useAuthStore()
const router = useRouter()
const schools = ref([])
const schoolId = ref(auth.isSchool ? auth.schoolId : undefined)
const tab = ref('pending')
const sid = ref(null)
const dialogs = ref()
const pending = ref([])
const abnormalRows = ref([])
const referrals = ref([])
const sm = ref({})
const byClass = ref([])
const byDevice = ref([])
const byVenue = ref([])

const loadStats = async () => {
  const d = await api.retestStats(auth.isSchool ? auth.schoolId : schoolId.value)
  sm.value = d.summary
  byClass.value = d.byClass
  byDevice.value = d.byDevice
  byVenue.value = d.byVenue
}

const load = async () => {
  const [p, ab, refs] = await Promise.all([
    api.retests('PENDING'),
    api.students({ stage: 'ABNORMAL_REVIEW' }),
    api.referrals('ALL')
  ])
  pending.value = p
  abnormalRows.value = ab
  referrals.value = refs
  await loadStats()
}

const openSubmit = (row) => {
  sid.value = row.studentId
  dialogs.value.open('retestSubmit', {
    retestId: row.id,
    deviceCalibNo: row.originalScreen?.deviceCalibNo,
    venue: row.originalScreen?.venue
  })
}
const openInit = (row) => { sid.value = row.id; dialogs.value.open('retestInit') }
const openReview = (row) => { sid.value = row.id; dialogs.value.open('review') }
const goDetail = (id) => router.push(`/students/${id}`)

const signalType = (s) =>
  s.includes('设备') || s.includes('现场') ? 'warning'
    : s.includes('个体') ? 'danger'
      : s.includes('并存') ? 'primary' : 'info'
const referralTagType = (st) => ({
  PENDING: 'warning', CONFIRMED: 'primary', VISITED: 'success', CLOSED: 'info'
}[st] || 'info')
const fmt = (t) => t ? t.replace('T', ' ').substring(0, 16) : ''

onMounted(async () => {
  if (!auth.isSchool) schools.value = await api.schools()
  await load()
})
</script>

<style scoped>
.filters { display: flex; gap: 10px; margin-bottom: 12px; }
</style>
