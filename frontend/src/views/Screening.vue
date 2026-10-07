<template>
  <div class="page-container">
    <el-row :gutter="12" style="margin-bottom:12px">
      <el-col :span="6"><el-card class="kpi-card"><div class="num">{{ stat.total }}</div><div class="label">在册学生</div></el-card></el-col>
      <el-col :span="6"><el-card class="kpi-card"><div class="num" style="color:#e6a23c">{{ stat.pending }}</div><div class="label">待筛查</div></el-card></el-col>
      <el-col :span="6"><el-card class="kpi-card"><div class="num" style="color:#f56c6c">{{ stat.absent }}</div><div class="label">请假待补检</div></el-card></el-col>
      <el-col :span="6"><el-card class="kpi-card"><div class="num" style="color:#67c23a">{{ stat.done }}</div><div class="label">已完成筛查</div></el-card></el-col>
    </el-row>

    <el-card>
      <el-radio-group v-model="stage" style="margin-bottom:10px" @change="load">
        <el-radio-button value="PENDING_SCREEN">待筛查</el-radio-button>
        <el-radio-button value="LOST_FOLLOW">请假/失访待补检</el-radio-button>
        <el-radio-button value="">全部学生</el-radio-button>
      </el-radio-group>
      <el-table :data="rows" stripe size="small">
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column prop="schoolName" label="学校" min-width="180" />
        <el-table-column label="班级" width="130">
          <template #default="{ row }">{{ row.grade }}{{ row.className }}</template>
        </el-table-column>
        <el-table-column prop="stageLabel" label="状态" width="120" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="pick(row)">录入筛查/补检</el-button>
            <el-button link size="small" @click="detail(row)">档案</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top:12px">
      <template #header>
        <span class="card-header-title">待复测任务（二次确认）
          <el-tag v-if="retestTasks.length" type="warning" size="small" style="margin-left:6px">{{ retestTasks.length }}</el-tag>
        </span>
      </template>
      <el-alert v-if="retestTasks.length" type="warning" :closable="false" style="margin-bottom:10px"
        title="健康老师已对以下学生的异常初测发起复测，请使用校准后的设备重新检测并录入复测结果。" />
      <el-table :data="retestTasks" stripe size="small">
        <el-table-column prop="studentName" label="姓名" width="90" />
        <el-table-column label="学校/班级" min-width="180">
          <template #default="{ row }">{{ row.schoolName }} {{ row.grade }}{{ row.className }}</template>
        </el-table-column>
        <el-table-column label="初测异常数据" min-width="190">
          <template #default="{ row }">
            裸眼 左{{ row.originalScreen?.nakedVisionLeft || '—' }}/右{{ row.originalScreen?.nakedVisionRight || '—' }}，
            球镜 左{{ row.originalScreen?.sphereLeft || '—' }}/右{{ row.originalScreen?.sphereRight || '—' }}
            <div class="muted">设备 {{ row.originalScreen?.deviceCalibNo || '—' }} ｜ {{ row.originalScreen?.screenDate }}</div>
          </template>
        </el-table-column>
        <el-table-column label="复测原因" min-width="140">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ row.reasonLabel }}</el-tag>
            <div class="muted">{{ row.reasonDetail }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="warning" size="small" @click="completeRetest(row)">录入复测结果</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!retestTasks.length" description="暂无待复测任务" :image-size="60" />
    </el-card>

    <action-dialogs ref="dialogs" :student-id="sid" @done="load" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import ActionDialogs from '../components/ActionDialogs.vue'

const router = useRouter()
const rows = ref([])
const stage = ref('PENDING_SCREEN')
const sid = ref(null)
const dialogs = ref()
const stat = ref({ total: 0, pending: 0, absent: 0, done: 0 })
const retestTasks = ref([])

const load = async () => {
  rows.value = await api.students({ stage: stage.value || null })
  const all = await api.students({})
  stat.value = {
    total: all.length,
    pending: all.filter(s => s.stage === 'PENDING_SCREEN').length,
    absent: all.filter(s => s.stage === 'LOST_FOLLOW').length,
    done: all.filter(s => s.stage !== 'PENDING_SCREEN').length
  }
  retestTasks.value = await api.retests('PENDING')
}
const pick = (row) => { sid.value = row.id; dialogs.value.open('screen') }
const completeRetest = (row) => {
  sid.value = row.studentId
  dialogs.value.open('retestComplete', { taskId: row.id, original: row.originalScreen })
}
const detail = (row) => router.push(`/students/${row.id}`)
onMounted(load)
</script>
