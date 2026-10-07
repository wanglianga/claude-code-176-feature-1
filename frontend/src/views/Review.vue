<template>
  <div class="page-container">
    <el-card>
      <el-tabs v-model="tab">
        <el-tab-pane label="待健康老师复核" name="review">
          <el-table :data="reviewRows" stripe size="small">
            <el-table-column prop="name" label="姓名" width="90" />
            <el-table-column label="学校/班级" min-width="200">
              <template #default="{ row }">{{ row.schoolName }} {{ row.grade }}{{ row.className }}</template>
            </el-table-column>
            <el-table-column label="异常标记" min-width="160">
              <template #default="{ row }">
                <el-tag v-for="(f,i) in row.flagLabels" :key="i" type="danger" size="small" style="margin:2px">{{ f }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="pick(row,'review')">复核</el-button>
                <el-button type="warning" size="small" plain @click="pick(row,'retestInitiate')">发起复测</el-button>
                <el-button link size="small" @click="detail(row)">查看档案</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="复测二次确认" name="retest">
          <el-alert type="warning" :closable="false" style="margin-bottom:10px"
            title="设备遮挡、佩戴不规范或环境光线导致的异常初测，先复测再决定是否告知家长：复测仍异常才生成家长复诊提醒，复测恢复正常则留下异常原因并关闭本次转诊任务（已发出的通知保留并标记取消）。" />
          <el-table :data="pendingTasks" stripe size="small">
            <el-table-column prop="studentName" label="姓名" width="90" />
            <el-table-column label="班级" width="130">
              <template #default="{ row }">{{ row.grade }}{{ row.className }}</template>
            </el-table-column>
            <el-table-column label="初测异常数据" min-width="200">
              <template #default="{ row }">
                裸眼 左{{ row.originalScreen?.nakedVisionLeft || '—' }}/右{{ row.originalScreen?.nakedVisionRight || '—' }}，
                球镜 左{{ row.originalScreen?.sphereLeft || '—' }}/右{{ row.originalScreen?.sphereRight || '—' }}
                <div class="muted">设备 {{ row.originalScreen?.deviceCalibNo || '—' }} ｜ {{ row.originalScreen?.screenDate }}</div>
              </template>
            </el-table-column>
            <el-table-column label="复测原因" min-width="150">
              <template #default="{ row }">
                <el-tag type="warning" size="small">{{ row.reasonLabel }}</el-tag>
                <div class="muted">{{ row.reasonDetail }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="initiator" label="发起人" width="160" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button type="warning" size="small" @click="completeRetest(row)">录入复测结果</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!pendingTasks.length" description="暂无待复测任务" :image-size="60" />

          <el-divider content-position="left">已完成复测（两次数据均保留）</el-divider>
          <el-table :data="doneTasks" stripe size="small">
            <el-table-column prop="studentName" label="姓名" width="90" />
            <el-table-column label="班级" width="120">
              <template #default="{ row }">{{ row.grade }}{{ row.className }}</template>
            </el-table-column>
            <el-table-column label="初测 → 复测" min-width="260">
              <template #default="{ row }">
                <span>裸眼 {{ row.originalScreen?.nakedVisionLeft }}/{{ row.originalScreen?.nakedVisionRight }}
                  → {{ row.retestScreen?.nakedVisionLeft }}/{{ row.retestScreen?.nakedVisionRight }}</span>
                <div class="muted">
                  设备 {{ row.originalScreen?.deviceCalibNo || '—' }} → {{ row.retestScreen?.deviceCalibNo || '—' }}
                  ｜ 操作人 {{ row.retestScreen?.operatorName || '—' }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="结论" min-width="200">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'CLEARED_NORMAL' ? 'success' : 'danger'">{{ row.statusLabel }}</el-tag>
                <div class="muted">{{ row.conclusion }}</div>
              </template>
            </el-table-column>
            <el-table-column label="复测原因" width="130">
              <template #default="{ row }"><el-tag size="small" type="warning" effect="plain">{{ row.reasonLabel }}</el-tag></template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!doneTasks.length" description="暂无已完成复测" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="复测比例统计" name="retestStat">
          <el-alert type="info" :closable="false" style="margin-bottom:10px"
            title="按班级和设备查看复测比例：某设备/班级复测后多恢复正常，提示设备、光线或现场组织问题；复测仍异常为主则倾向学生个体问题，按流程转诊。" />
          <el-radio-group v-model="statGroup" style="margin-bottom:10px" @change="loadStats">
            <el-radio-button value="class">按班级</el-radio-button>
            <el-radio-button value="device">按设备</el-radio-button>
          </el-radio-group>
          <el-table :data="statRows" stripe size="small">
            <template v-if="statGroup === 'class'">
              <el-table-column prop="schoolName" label="学校" min-width="160" />
              <el-table-column label="班级" width="120">
                <template #default="{ row }">{{ row.grade }}{{ row.className }}</template>
              </el-table-column>
            </template>
            <el-table-column v-else prop="deviceCalibNo" label="设备校准编号" min-width="140" />
            <el-table-column prop="screened" label="初测数" width="80" />
            <el-table-column prop="abnormal" label="异常数" width="80" />
            <el-table-column prop="retestInitiated" label="发起复测" width="90" />
            <el-table-column label="复测率" width="90">
              <template #default="{ row }">{{ row.retestRate }}%</template>
            </el-table-column>
            <el-table-column prop="pending" label="待复测" width="80" />
            <el-table-column prop="confirmedAbnormal" label="复测仍异常" width="100" />
            <el-table-column prop="clearedNormal" label="复测正常" width="90" />
            <el-table-column label="复测正常率" width="110">
              <template #default="{ row }">
                <el-tag :type="row.clearedRate >= 50 && row.retestDone > 0 ? 'danger' : 'success'" size="small">
                  {{ row.retestDone ? row.clearedRate + '%' : '—' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="研判" min-width="220">
              <template #default="{ row }">
                <el-tag v-if="row.suspicious" type="danger" size="small">疑似设备/现场因素</el-tag>
                <span class="muted" style="margin-left:4px">{{ row.hint }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="学校日常干预（座位/户外/提醒）" name="intervene">
          <el-alert type="info" :closable="false" style="margin-bottom:10px"
            title="把医疗复诊与学校日常干预连接起来：对异常及已配镜学生持续安排座位调整、户外活动、家长提醒和用眼指导。" />
          <el-table :data="manageRows" stripe size="small">
            <el-table-column prop="name" label="姓名" width="90" />
            <el-table-column label="班级" width="140">
              <template #default="{ row }">{{ row.grade }}{{ row.className }}</template>
            </el-table-column>
            <el-table-column prop="stageLabel" label="当前状态" width="120" />
            <el-table-column label="操作" width="260">
              <template #default="{ row }">
                <el-button size="small" @click="pick(row,'intervention')">登记干预</el-button>
                <el-button size="small" plain @click="quick(row,'SEAT_ADJUST','座位调整至教室前排')">座位调整</el-button>
                <el-button size="small" plain type="success" @click="quick(row,'OUTDOOR_ACTIVITY','每天保证2小时户外活动')">户外活动</el-button>
                <el-button size="small" plain type="warning" @click="quick(row,'PARENT_REMIND','向家长推送用眼与复查提醒')">家长提醒</el-button>
              </template>
            </el-table-column>
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
import { ElMessage } from 'element-plus'
import { api } from '../api'
import ActionDialogs from '../components/ActionDialogs.vue'

const router = useRouter()
const tab = ref('review')
const reviewRows = ref([])
const manageRows = ref([])
const pendingTasks = ref([])
const doneTasks = ref([])
const statRows = ref([])
const statGroup = ref('class')
const sid = ref(null)
const dialogs = ref()

const load = async () => {
  reviewRows.value = await api.students({ stage: 'ABNORMAL_REVIEW' })
  // 已进入管理链路、需要学校持续干预的学生
  const managed = await api.students({})
  manageRows.value = managed.filter(s => ['GLASSES_SUBSIDY','MANAGING','REVIEW_DUE','CLOSED_LOOP',
    'VISITED','AWAIT_VISIT','AWAIT_PARENT'].includes(s.stage))
  const tasks = await api.retests('ALL')
  pendingTasks.value = tasks.filter(t => t.status === 'PENDING')
  doneTasks.value = tasks.filter(t => t.status !== 'PENDING')
  await loadStats()
}
const loadStats = async () => { statRows.value = await api.retestStats(statGroup.value) }
const pick = (row, name) => { sid.value = row.id; dialogs.value.open(name) }
const completeRetest = (row) => {
  sid.value = row.studentId
  dialogs.value.open('retestComplete', { taskId: row.id, original: row.originalScreen })
}
const quick = async (row, type, content) => {
  await api.intervention({ studentId: row.id, type, content, executed: true })
  ElMessage.success(`已为 ${row.name} 登记干预`)
}
const detail = (row) => router.push(`/students/${row.id}`)
onMounted(load)
</script>
