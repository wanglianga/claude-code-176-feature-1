<template>
  <div>
    <!-- 1. 现场筛查 -->
    <el-dialog v-model="visible.screen" title="现场筛查记录" width="680px">
      <el-form :model="f.screen" label-width="130px">
        <el-row :gutter="10">
          <el-col :span="12"><el-form-item label="筛查日期"><el-date-picker v-model="f.screen.screenDate" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="设备校准编号"><el-input v-model="f.screen.deviceCalibNo" placeholder="如 DEV-2026-0091"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="筛查场地"><el-input v-model="f.screen.venue" placeholder="如 校医室/阶梯教室/操场"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="裸眼视力 左"><el-input v-model="f.screen.nakedVisionLeft" placeholder="如 4.6"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="裸眼视力 右"><el-input v-model="f.screen.nakedVisionRight" placeholder="如 4.7"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="球镜 左(D)"><el-input v-model="f.screen.sphereLeft" placeholder="如 -1.50"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="球镜 右(D)"><el-input v-model="f.screen.sphereRight"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="散光柱镜 左"><el-input v-model="f.screen.cylinderLeft"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="散光柱镜 右"><el-input v-model="f.screen.cylinderRight"/></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="是否戴镜"><el-switch v-model="f.screen.wearingGlasses"/></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需要复测"><el-switch v-model="f.screen.retestNeeded"/></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="用眼习惯问卷"><el-input v-model="f.screen.habitSummary" type="textarea"
              :rows="2" placeholder="每日近距离用眼时长/电子产品/户外活动/读写姿势"/></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="筛查机构/操作人">
              <el-input v-model="f.screen.orgName" placeholder="机构" style="width:48%"/>
              <el-input v-model="f.screen.operatorName" placeholder="操作人" style="width:48%;margin-left:4%"/>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-alert type="warning" :closable="false"
              title="若学生当日请假未检，请勾选下方——平台将登记“筛查请假”异常事项并进入失访跟进，待补检后闭环。" />
            <el-form-item label="请假未检" style="margin-top:8px"><el-switch v-model="f.screen.absent"/></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible.screen=false">取消</el-button>
        <el-button type="primary" @click="submit('screen', api.screen)">提交筛查</el-button>
      </template>
    </el-dialog>

    <!-- 1b. 发起二次确认复测 -->
    <el-dialog v-model="visible.retestInit" title="发起二次确认复测（异常结果二次确认）" width="560px">
      <el-alert type="info" :closable="false" style="margin-bottom:12px"
        title="首次异常结果暂不推送家长。复测仍异常才生成家长复诊提醒；复测恢复正常则记录异常原因并关闭本次转诊任务。" />
      <el-form label-width="120px">
        <el-form-item label="复测原因" required>
          <el-select v-model="f.retestInit.reason" style="width:100%" placeholder="选择导致首次异常的现场原因">
            <el-option label="设备遮挡/读数异常" value="DEVICE_BLOCK" />
            <el-option label="佩戴不规范" value="WEAR_IRREGULAR" />
            <el-option label="环境光线影响" value="ENV_LIGHT" />
            <el-option label="学生状态波动/配合度差" value="STATE_FLUCTUATION" />
            <el-option label="其他现场原因" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="现场情况说明">
          <el-input v-model="f.retestInit.reasonDetail" type="textarea" :rows="3"
            placeholder="如：阶梯教室靠窗逆光、设备镜头被遮挡、学生未贴紧颌托等；可注明拟更换的设备/场地" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible.retestInit=false">取消</el-button>
        <el-button type="warning" @click="submit('retestInit', api.retestInitiate)">发起复测</el-button>
      </template>
    </el-dialog>

    <!-- 1c. 录入复测（第二次）数据 -->
    <el-dialog v-model="visible.retestSubmit" title="录入复测数据并给出结论" width="680px">
      <el-alert
        :title="retestContext.normalExpected
          ? '提示：平台将按同一标准判定。若复测恢复正常，将记录异常原因并关闭本次转诊任务（不删除、通知留痕）。'
          : '提示：若复测仍异常，将幂等生成家长复诊提醒，两次数据、设备编号、操作人全程保留。'"
        :type="retestContext.normalExpected ? 'success' : 'warning'" :closable="false" style="margin-bottom:10px" />
      <el-form :model="f.retestSubmit" label-width="130px">
        <el-row :gutter="10">
          <el-col :span="12"><el-form-item label="复测日期"><el-date-picker v-model="f.retestSubmit.retestDate" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="复测场地"><el-input v-model="f.retestSubmit.venue" placeholder="如 校医室"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="复测设备编号"><el-input v-model="f.retestSubmit.deviceCalibNo" placeholder="可换用已校准设备"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="复测操作人"><el-input v-model="f.retestSubmit.operatorName" placeholder="健康老师/筛查员"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="裸眼视力 左"><el-input v-model="f.retestSubmit.nakedVisionLeft" placeholder="如 5.0"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="裸眼视力 右"><el-input v-model="f.retestSubmit.nakedVisionRight" placeholder="如 5.0"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="球镜 左(D)"><el-input v-model="f.retestSubmit.sphereLeft" placeholder="如 0.00"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="球镜 右(D)"><el-input v-model="f.retestSubmit.sphereRight"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="散光柱镜 左"><el-input v-model="f.retestSubmit.cylinderLeft"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="散光柱镜 右"><el-input v-model="f.retestSubmit.cylinderRight"/></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="是否戴镜"><el-switch v-model="f.retestSubmit.wearingGlasses"/></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="复测结论说明"><el-input v-model="f.retestSubmit.conclusionNote" type="textarea" :rows="2"
              placeholder="将同步到家长可见档案：如 更换设备/遮蔽杂光后达标，或 规范佩戴后仍异常"/></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible.retestSubmit=false">取消</el-button>
        <el-button type="primary" @click="submit('retestSubmit', api.retestSubmit)">提交复测结论</el-button>
      </template>
    </el-dialog>

    <!-- 2. 学校复核 -->
    <el-dialog v-model="visible.review" title="学校健康老师复核" width="520px">
      <el-form label-width="90px">
        <el-form-item label="复核结论">
          <el-radio-group v-model="f.review.result">
            <el-radio value="CONFIRM">确认异常</el-radio>
            <el-radio value="REJECT">误判排除</el-radio>
            <el-radio value="RETEST">建议复测</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核意见"><el-input v-model="f.review.opinion" type="textarea" :rows="3"/></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible.review=false">取消</el-button>
        <el-button type="primary" @click="submit('review', api.schoolReview)">提交复核</el-button>
      </template>
    </el-dialog>

    <!-- 3. 家长告知 -->
    <el-dialog v-model="visible.notify" title="家长告知" width="520px">
      <el-form label-width="110px">
        <el-form-item label="告知方式">
          <el-select v-model="f.notify.channel" style="width:100%">
            <el-option label="短信" value="短信" /><el-option label="电话" value="电话" />
            <el-option label="APP" value="APP" /><el-option label="短信+APP" value="短信+APP" />
            <el-option label="家长会告知" value="家长会告知" />
          </el-select>
        </el-form-item>
        <el-form-item label="本次未接通">
          <el-switch v-model="f.notify.unreachable" />
          <span class="muted" style="margin-left:8px">勾选后累计无法接通次数并登记异常事项</span>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="f.notify.remark" type="textarea" :rows="2"/></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible.notify=false">取消</el-button>
        <el-button type="primary" @click="submit('notify', api.notify)">记录告知</el-button>
      </template>
    </el-dialog>

    <!-- 4. 家长确认选择 -->
    <el-dialog v-model="visible.choice" title="家长确认与处置选择" width="540px">
      <el-form label-width="110px">
        <el-form-item label="处置选择">
          <el-radio-group v-model="f.choice.choice">
            <el-radio value="COUNTY_HOSPITAL">县医院复诊</el-radio>
            <el-radio value="PARTNER_CLINIC">合作眼科门诊复诊</el-radio>
            <el-radio value="DEFER">暂缓处理</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="f.choice.choice !== 'DEFER'" label="复诊机构">
          <el-input v-model="f.choice.targetOrg" placeholder="如 阳光县人民医院眼科 / 爱眼眼科门诊"/>
        </el-form-item>
        <el-form-item v-if="f.choice.choice === 'DEFER'" label="暂缓原因">
          <el-input v-model="f.choice.deferReason" type="textarea" :rows="3" placeholder="必填"/>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="f.choice.remark" type="textarea" :rows="2"/></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible.choice=false">取消</el-button>
        <el-button type="primary" @click="submit('choice', api.parentChoice)">提交家长选择</el-button>
      </template>
    </el-dialog>

    <!-- 5. 复诊处方 -->
    <el-dialog v-model="visible.visit" title="复诊结果与处方回写" width="720px">
      <el-form :model="f.visit" label-width="130px">
        <el-row :gutter="10">
          <el-col :span="12"><el-form-item label="复诊机构"><el-input v-model="f.visit.orgName"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="医生"><el-input v-model="f.visit.doctor"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="复诊日期"><el-date-picker v-model="f.visit.visitDate" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="瞳距(mm)"><el-input v-model="f.visit.pd"/></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="真性近视"><el-switch v-model="f.visit.trueMyopia"/></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需要散瞳"><el-switch v-model="f.visit.needMydriasis"/></el-form-item>
          </el-col>
          <el-col :span="6"><el-form-item label="球镜左"><el-input v-model="f.visit.rxSphereLeft"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="球镜右"><el-input v-model="f.visit.rxSphereRight"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="柱镜左"><el-input v-model="f.visit.rxCylinderLeft"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="柱镜右"><el-input v-model="f.visit.rxCylinderRight"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="轴位左"><el-input v-model="f.visit.rxAxisLeft"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="轴位右"><el-input v-model="f.visit.rxAxisRight"/></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="复查周期(月)"><el-input-number v-model="f.visit.reviewCycleMonths" :min="1" :max="24"/></el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="诊断结论"><el-input v-model="f.visit.diagnosis" type="textarea" :rows="2"/></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="符合配镜补助"><el-switch v-model="f.visit.subsidyEligible"/></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="与筛查差异大"><el-switch v-model="f.visit.bigDifference"/></el-form-item>
          </el-col>
          <el-col :span="24" v-if="f.visit.bigDifference">
            <el-form-item label="差异说明"><el-input v-model="f.visit.differenceNote" type="textarea" :rows="2"
              placeholder="平台将登记“复诊与筛查差异较大”异常事项，供卫健核对设备与判定"/></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible.visit=false">取消</el-button>
        <el-button type="success" @click="submit('visit', api.visit)">回写复诊结果</el-button>
      </template>
    </el-dialog>

    <!-- 6. 配镜 -->
    <el-dialog v-model="visible.glasses" title="配镜登记" width="580px">
      <el-form label-width="120px">
        <el-form-item label="配镜机构"><el-input v-model="f.glasses.opticianOrg"/></el-form-item>
        <el-form-item label="配镜日期"><el-date-picker v-model="f.glasses.glassesDate" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
        <el-form-item label="机构性质">
          <el-radio-group v-model="f.glasses.partnerOptician">
            <el-radio :value="true">合作机构</el-radio>
            <el-radio :value="false">家长自行外配</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额(元)"><el-input-number v-model="f.glasses.amount" :min="0" :precision="2"/></el-form-item>
        <el-form-item label="镜片/处方信息"><el-input v-model="f.glasses.lensInfo" type="textarea" :rows="2"/></el-form-item>
        <el-form-item label="配镜后不适"><el-switch v-model="f.glasses.discomfort"/></el-form-item>
        <el-form-item v-if="f.glasses.discomfort" label="不适描述">
          <el-input v-model="f.glasses.discomfortDetail" type="textarea" :rows="2"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible.glasses=false">取消</el-button>
        <el-button type="success" @click="submit('glasses', api.glasses)">登记配镜</el-button>
      </template>
    </el-dialog>

    <!-- 7. 补助申请 -->
    <el-dialog v-model="visible.subsidy" title="配镜补助申请" width="480px">
      <el-form label-width="110px">
        <el-form-item label="申请金额(元)"><el-input-number v-model="f.subsidy.applyAmount" :min="0" :precision="2"/></el-form-item>
        <el-form-item label="说明"><el-input v-model="f.subsidy.opinion" type="textarea" :rows="3"
          placeholder="外配眼镜需在此说明验光凭证情况"/></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible.subsidy=false">取消</el-button>
        <el-button type="warning" @click="submit('subsidy', api.subsidyApply)">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 8. 复查随访 -->
    <el-dialog v-model="visible.followup" title="复查随访登记" width="520px">
      <el-form label-width="100px">
        <el-form-item label="处理">
          <el-radio-group v-model="f.followup.action">
            <el-radio value="DONE">已完成复查</el-radio>
            <el-radio value="NO_SHOW">未到场（催办）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="f.followup.action==='DONE'" label="实际复查日期">
          <el-date-picker v-model="f.followup.actualDate" value-format="YYYY-MM-DD" style="width:100%"/>
        </el-form-item>
        <el-form-item v-else label="计划日期">
          <el-date-picker v-model="f.followup.plannedDate" value-format="YYYY-MM-DD" style="width:100%"/>
        </el-form-item>
        <el-form-item label="复查机构"><el-input v-model="f.followup.orgName"/></el-form-item>
        <el-form-item v-if="f.followup.action==='DONE'" label="视力结果"><el-input v-model="f.followup.visionResult"/></el-form-item>
        <el-form-item label="备注"><el-input v-model="f.followup.remark" type="textarea" :rows="2"/></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible.followup=false">取消</el-button>
        <el-button type="warning" @click="submit('followup', api.followup)">提交</el-button>
      </template>
    </el-dialog>

    <!-- 9. 学校干预 -->
    <el-dialog v-model="visible.intervention" title="学校日常干预登记" width="520px">
      <el-form label-width="100px">
        <el-form-item label="干预类型">
          <el-select v-model="f.intervention.type" style="width:100%">
            <el-option label="座位调整" value="SEAT_ADJUST" />
            <el-option label="户外活动" value="OUTDOOR_ACTIVITY" />
            <el-option label="家长提醒" value="PARENT_REMIND" />
            <el-option label="用眼行为指导" value="BEHAVIOR_GUIDE" />
            <el-option label="其他干预" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行日期"><el-date-picker v-model="f.intervention.execDate" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
        <el-form-item label="干预内容"><el-input v-model="f.intervention.content" type="textarea" :rows="3"
          placeholder="如：调整至前排座位 / 每日2小时户外活动 / 向家长推送用眼提醒"/></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible.intervention=false">取消</el-button>
        <el-button type="info" @click="submit('intervention', api.intervention)">登记干预</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const props = defineProps({ studentId: [String, Number] })
const emit = defineEmits(['done'])

const visible = reactive({})
const today = new Date().toISOString().slice(0, 10)
// 复测弹窗上下文：retestId（录入结果）、originalScreenId（指定原记录发起）
const retestContext = ref({ retestId: null, originalScreenId: null, normalExpected: false })

const blank = () => ({
  screen: { screenDate: today, orgName: '阳光县视光中心', operatorName: '', deviceCalibNo: '',
    venue: '校医室', nakedVisionLeft: '', nakedVisionRight: '', sphereLeft: '', sphereRight: '',
    cylinderLeft: '', cylinderRight: '', wearingGlasses: false, habitSummary: '',
    retestNeeded: false, absent: false },
  retestInit: { reason: 'DEVICE_BLOCK', reasonDetail: '' },
  retestSubmit: { retestDate: today, orgName: '阳光县视光中心', operatorName: '', deviceCalibNo: '',
    venue: '校医室', nakedVisionLeft: '', nakedVisionRight: '', sphereLeft: '', sphereRight: '',
    cylinderLeft: '', cylinderRight: '', wearingGlasses: false, conclusionNote: '' },
  review: { result: 'CONFIRM', opinion: '' },
  notify: { channel: '电话', unreachable: false, remark: '' },
  choice: { choice: 'COUNTY_HOSPITAL', targetOrg: '阳光县人民医院眼科', deferReason: '', remark: '' },
  visit: { visitDate: today, orgName: '阳光县人民医院眼科', doctor: '', trueMyopia: true,
    needMydriasis: false, rxSphereLeft: '', rxSphereRight: '', rxCylinderLeft: '', rxCylinderRight: '',
    rxAxisLeft: '', rxAxisRight: '', pd: '', diagnosis: '', reviewCycleMonths: 6,
    subsidyEligible: true, bigDifference: false, differenceNote: '' },
  glasses: { glassesDate: today, opticianOrg: '', partnerOptician: true, amount: 600,
    lensInfo: '', discomfort: false, discomfortDetail: '' },
  subsidy: { applyAmount: 300, opinion: '' },
  followup: { action: 'DONE', plannedDate: today, actualDate: today, orgName: '', visionResult: '', remark: '' },
  intervention: { type: 'SEAT_ADJUST', execDate: today, content: '' }
})
const f = reactive(blank())

const open = (name, ctx = {}) => {
  // 重置为默认值
  const d = blank()
  Object.keys(f).forEach(k => { Object.assign(f[k], d[k]) })
  retestContext.value = { retestId: null, originalScreenId: null, normalExpected: false, ...ctx }
  // 发起复测时可预填原记录
  if (name === 'retestInit' && ctx.originalScreenId) {
    f.retestInit.originalScreenId = ctx.originalScreenId
  }
  // 录入复测结果时预填复测设备/操作人（默认沿用首次，可现场更换）
  if (name === 'retestSubmit') {
    if (ctx.deviceCalibNo) f.retestSubmit.deviceCalibNo = ctx.deviceCalibNo
    if (ctx.venue) f.retestSubmit.venue = ctx.venue
  }
  visible[name] = true
}
defineExpose({ open })

const submit = async (name, call) => {
  try {
    let payload
    if (name === 'retestSubmit') {
      payload = { retestId: retestContext.value.retestId, ...f[name] }
    } else if (name === 'retestInit') {
      payload = {
        ...(retestContext.value.originalScreenId
          ? { originalScreenId: retestContext.value.originalScreenId }
          : { studentId: props.studentId }),
        ...f[name]
      }
    } else {
      payload = { studentId: props.studentId, ...f[name] }
    }
    await call(payload)
    visible[name] = false
    ElMessage.success('提交成功')
    emit('done')
  } catch (e) { /* 拦截器已提示 */ }
}
</script>
