<template>
  <div class="full-content">
    <a-card :bodyStyle="{ padding: '10px' }">
      <template #title>
        <a-space>
          <a-input class="search-input-item" @pressEnter="getMachineList" v-model="listQuery['%name%']"
            placeholder="机器名称" />
          <a-input class="search-input-item" @pressEnter="getMachineList" v-model="listQuery['%jpomUrl%']"
            placeholder="节点地址" />
          <a-input class="search-input-item" @pressEnter="getMachineList" v-model="listQuery['%jpomVersion%']"
            placeholder="插件版本" />
          <a-select show-search option-filter-prop="children" v-model="listQuery.groupName" allowClear placeholder="分组"
            class="search-input-item">
            <a-select-option v-for="item in groupList" :key="item">{{ item }}</a-select-option>
          </a-select>
          <a-select v-model="listQuery['order_field']" allowClear placeholder="请选择排序字段" class="search-input-item">
            <a-select-option value="networkDelay">网络延迟</a-select-option>
            <a-select-option value="osOccupyCpu">cpu</a-select-option>
            <a-select-option value="osOccupyDisk">硬盘</a-select-option>
            <a-select-option value="osOccupyMemory">内存</a-select-option>
            <a-select-option value="modifyTimeMillis">更新时间</a-select-option>
            <a-select-option value="createTimeMillis">创建时间</a-select-option>
          </a-select>
          <a-button :loading="loading" type="primary" @click="getMachineList">搜索</a-button>
          <a-button type="primary" @click="addMachine">添加机器</a-button>

          <a-dropdown v-if="layoutType === 'table'">
            <template #overlay>
              <a-menu>
                <a-menu-item key="1" @click="syncToWorkspaceShow"> 分配节点 </a-menu-item>
                <a-menu-item key="2" @click="syncNodeWhiteConfig"> 同步白名单 </a-menu-item>
                <a-menu-item key="3" @click="syncNodeConfig"> 同步系统配置 </a-menu-item>
              </a-menu>
            </template>
            <a-button type="primary"> 批量操作 <down-outlined /> </a-button>
          </a-dropdown>
          <a-tooltip v-else title="表格视图才能使用同步配置功能">
            <a-button :disabled="true" type="primary"> 批量操作 <down-outlined /> </a-button>
          </a-tooltip>
          <a-button type="primary" @click="changeLayout">
            {{ layoutType === 'card' ? '卡片' : '表格' }}
            <template #icon>
              <layout-outlined v-if="layoutType === 'card'" />
              <table-outlined v-else />
            </template>
          </a-button>
          <a-tooltip>
            <template #title>
              <ul>
                <li>节点账号密码为插件端的账号密码,并非用户账号(管理员)密码</li>
                <li>
                  节点账号密码默认由系统生成：可以通过插件端数据目录下 agent_authorize.json
                  文件查看（如果自定义配置了账号密码将没有此文件）
                </li>
                <li>节点地址为插件端的 IP:PORT 插件端端口默认为：2123</li>
              </ul>
            </template>
            <question-circle-filled />
          </a-tooltip>
        </a-space>
      </template>
      <!-- 卡片视图 -->
      <template v-if="layoutType === 'card'">
        <a-row>
          <a-row :gutter="[16, 16]">
            <template v-if="list && list.length">
              <a-col v-for="item in list" :key="item.id" :span="6">
                <template>
                  <a-card :headStyle="{ padding: '0 6px' }" :bodyStyle="{ padding: '10px' }">
                    <template #title>
                      <a-row :gutter="[4, 0]">
                        <a-col :span="17" style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                          <a-tooltip>
                            <template #title>
                              <div>节点名称：{{ item.name }}</div>
                              <div>节点地址：{{ item.jpomUrl }}</div>
                            </template>
                            <span @click="showMachineInfo(item)" style="cursor: pointer">
                              {{ item.name }}
                            </span>
                          </a-tooltip>
                        </a-col>
                        <a-col :span="7" style="text-align: right" class="text-overflow-hidden">
                          <a-tooltip :title="`当前状态：${statusMap[item.status]} ${item.statusMsg ? '状态消息：' + item.statusMsg : ''
                            } `">
                            <a-tag :color="item.status === 1 ? 'green' : 'pink'" style="margin-right: 0">
                              {{ statusMap[item.status] }}</a-tag>
                          </a-tooltip>
                        </a-col>
                      </a-row>
                    </template>

                    <a-tooltip :title="item.osName">
                      <a-row class="item-info">
                        <a-col :span="6" class="title text-overflow-hidden">系统名称:</a-col>
                        <a-col :span="18" class="content text-overflow-hidden">
                          <span style="color: #40a9ff; cursor: pointer" @click="showMachineInfo(item)">
                            {{ item.osName }}
                          </span>
                        </a-col>
                      </a-row>
                    </a-tooltip>
                    <a-tooltip :title="item.osVersion">
                      <a-row class="item-info">
                        <a-col :span="6" class="title text-overflow-hidden">系统版本:</a-col>
                        <a-col :span="18" class="content text-overflow-hidden">
                          {{ item.osVersion }}
                        </a-col>
                      </a-row>
                    </a-tooltip>
                    <a-tooltip :title="item.osLoadAverage">
                      <a-row class="item-info">
                        <a-col :span="6" class="title text-overflow-hidden">系统负载:</a-col>
                        <a-col :span="18" class="content text-overflow-hidden">
                          {{ item.osLoadAverage }}
                        </a-col>
                      </a-row>
                    </a-tooltip>
                    <a-tooltip :title="item.jpomVersion">
                      <a-row class="item-info">
                        <a-col :span="6" class="title text-overflow-hidden">插件版本:</a-col>
                        <a-col :span="18" class="content text-overflow-hidden">
                          <a-button v-if="item.jpomVersion" type="link" size="small" @click="showMachineUpgrade(item)">
                            {{ item.jpomVersion || '-' }}
                          </a-button>
                        </a-col>
                      </a-row>
                    </a-tooltip>
                    <a-row type="flex" align="middle" justify="center" style="margin-top: 10px">
                      <a-button-group>
                        <a-button @click="handleEdit(item)" type="primary" size="small"> 编辑 </a-button>
                        <a-button @click="showMachineInfo(item)" type="primary" size="small">详情</a-button>
                        <a-button @click="syncToWorkspaceShow(item)" type="primary" size="small">分配</a-button>
                        <a-button @click="viewMachineNode(item)" type="primary" size="small">节点</a-button>
                        <a-button @click="deleteMachineInfo(item)" size="small">删除</a-button>
                      </a-button-group>
                    </a-row>
                  </a-card>
                </template>
              </a-col>
            </template>
            <a-col v-else :span="24">
              <a-empty description="没有任何节点" />
            </a-col>
          </a-row>
        </a-row>
        <a-row type="flex" justify="center">
          <a-divider v-if="listQuery.total / listQuery.limit > 1" dashed />
          <a-col>
            <a-pagination v-model="listQuery.page" :showTotal="(total: number) => {
                return PAGE_DEFAULT_SHOW_TOTAL(total, listQuery.value)
              }
              " :showSizeChanger="true" :pageSizeOptions="sizeOptions" :pageSize="listQuery.limit"
              :total="listQuery.total" :hideOnSinglePage="true" @showSizeChange="(current, size) => {
                  listQuery.value.limit = size
                  getMachineList()
                }
                " @change="getMachineList" show-less-items />
          </a-col>
        </a-row>
      </template>
      <!-- 表格视图 -->
      <template v-else-if="layoutType === 'table'">
        <a-table :columns="columns" :data-source="list" bordered size="middle" rowKey="id" class="table"
          :pagination="pagination" @change="changePage" :row-selection="rowSelection">
          <a-tooltip #name slot-scope="text, item" :title="text">
            <a-button style="padding: 0" type="link" size="small" @click="showMachineInfo(item)"> {{ text }}</a-button>
          </a-tooltip>
          <a-tooltip #tooltip slot-scope="text" :title="text">
            <span>{{ text }}</span>
          </a-tooltip>
          <a-tooltip #status slot-scope="text, item"
            :title="`当前状态：${statusMap[item.status]} ${item.statusMsg ? '状态消息：' + item.statusMsg : ''} `">
            <a-tag :color="item.status === 1 ? 'green' : 'pink'" style="margin-right: 0">
              {{ statusMap[item.status] }}</a-tag>
          </a-tooltip>
          <a-tooltip #duration slot-scope="text" placement="topLeft" :title="formatDuration(text)">
            <span>{{ formatDuration(text, '', 2) }}</span>
          </a-tooltip>
          <a-tooltip #duration2 slot-scope="text" placement="topLeft" :title="formatDuration((text || 0) * 1000)">
            <span>{{ formatDuration((text || 0) * 1000, '', 2) }}</span>
          </a-tooltip>
          <a-tooltip #percent2Number slot-scope="text" placement="topLeft"
            :title="`${(text && formatPercent2Number(text) + '%') || '-'}`">
            <span>{{ (text && formatPercent2Number(text) + '%') || '-' }}</span>
          </a-tooltip>

          <template #operation slot-scope="text, record">
            <a-space>
              <a-button type="primary" size="small" @click="handleEdit(record)">编辑</a-button>
              <a-button @click="syncToWorkspaceShow(record)" type="primary" size="small">分配</a-button>
            </a-space>
          </template>
        </a-table>
      </template>
    </a-card>
    <!-- 编辑区 -->
    <a-modal destroyOnClose v-model:visible="editVisible" width="50%" title="编辑机器" @ok="handleEditOk"
      :maskClosable="false">
      <a-form ref="editNodeForm" :rules="rules" :model="temp" :label-col="{ span: 4 }" :wrapper-col="{ span: 19 }">
        <a-form-item label="机器名称" prop="name">
          <a-input :maxLength="50" v-model="temp.name" placeholder="机器名称" />
        </a-form-item>
        <a-form-item label="机器分组" prop="groupName">
          <custom-select v-model="temp.groupName" :data="groupList" suffixIcon="" inputPlaceholder="添加分组"
            selectPlaceholder="选择分组名">
          </custom-select>
        </a-form-item>

        <a-form-item prop="jpomUrl">
          <template #label>
            节点地址
            <a-tooltip v-show="!temp.id">
              <template #title>节点地址为插件端的 IP:PORT 插件端端口默认为：2123
                <ul>
                  <li>节点地址建议使用内网地址</li>
                  <li>如果插件端正常运行但是连接失败请检查端口是否开放,防火墙规则,云服务器的安全组入站规则</li>
                </ul>
              </template>
              <question-circle-filled />
            </a-tooltip>
          </template>
          <a-input v-model="temp.jpomUrl" placeholder="节点地址 (127.0.0.1:2123)">
            <a-select placeholder="协议类型" #addonBefore v-model="temp.jpomProtocol" default-value="Http://"
              style="width: 160px">
              <a-select-option value="Http"> Http:// </a-select-option>
              <a-select-option value="Https"> Https:// </a-select-option>
            </a-select>
          </a-input>
        </a-form-item>

        <a-form-item label="节点账号" prop="loginName">
          <a-input v-model="temp.jpomUsername" placeholder="节点账号,请查看节点启动输出的信息" />
        </a-form-item>
        <a-form-item :prop="`${temp.id ? 'loginPwd-update' : 'loginPwd'}`">
          <template #label>
            节点密码
            <a-tooltip v-show="!temp.id">
              <template #title>
                节点账号密码默认由系统生成：可以通过插件端数据目录下 agent_authorize.json
                文件查看（如果自定义配置了账号密码将没有此文件）
              </template>
              <question-circle-filled />
            </a-tooltip>
          </template>
          <a-input-password v-model="temp.jpomPassword" placeholder="节点密码,请查看节点启动输出的信息" />
        </a-form-item>

        <a-collapse>
          <a-collapse-panel key="1" header="其他配置">
            <a-form-item label="模板节点" prop="templateNode" help="">
              <a-switch checked-children="是" un-checked-children="否" default-checked v-model="temp.templateNode" />
              以此机器节点配置为模板,用于快捷同步其他机器节点的配置
            </a-form-item>

            <a-form-item label="超时时间(s)" prop="timeOut">
              <a-input-number v-model="temp.jpomTimeout" :min="0" placeholder="秒 (值太小可能会取不到节点状态)" style="width: 100%" />
            </a-form-item>

            <a-form-item label="代理" prop="jpomHttpProxy">
              <a-input v-model="temp.jpomHttpProxy" placeholder="代理地址 (127.0.0.1:8888)">
                <a-select #addonBefore v-model="temp.jpomHttpProxyType" placeholder="选择代理类型" default-value="HTTP"
                  style="width: 100px">
                  <a-select-option value="HTTP">HTTP</a-select-option>
                  <a-select-option value="SOCKS">SOCKS</a-select-option>
                  <a-select-option value="DIRECT">DIRECT</a-select-option>
                </a-select>
              </a-input>
            </a-form-item>

            <a-form-item label="编码方式" prop="transportEncryption">
              <a-select show-search default-value="0" v-model="temp.transportEncryption" placeholder="请选择编码方式">
                <a-select-option :value="0">不编码</a-select-option>
                <a-select-option :value="1">BASE64</a-select-option>
                <a-select-option :value="2">AES</a-select-option>
              </a-select>
            </a-form-item>
          </a-collapse-panel>
        </a-collapse>
      </a-form>
    </a-modal>
    <!-- 机器信息组件 -->
    <a-drawer destroyOnClose title="机器详情" placement="right"
      :width="`${this.getCollapsed ? 'calc(100vw - 80px)' : 'calc(100vw - 200px)'}`" :visible="drawerVisible" @close="() => {
          drawerVisible.value = false
        }
        ">
      <!-- 机器信息组件 -->
      <machine-info v-if="drawerVisible" :machineId="temp.id" />
    </a-drawer>
    <!-- 分配到其他工作空间 -->
    <a-modal destroyOnClose v-model="syncToWorkspaceVisible" title="分配到其他工作空间" @ok="handleSyncToWorkspace"
      :maskClosable="false">
      <!-- <a-alert message="温馨提示" type="warning">
        <template #description> </template>
      </a-alert> -->
      <a-form :model="temp" :label-col="{ span: 6 }" :wrapper-col="{ span: 14 }">
        <a-form-item> </a-form-item>
        <a-form-item label="选择工作空间" prop="workspaceId">
          <a-select show-search option-filter-prop="children" v-model="temp.workspaceId" placeholder="请选择工作空间">
            <a-select-option v-for="item in workspaceList" :key="item.id">{{ item.name }}</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
    <!-- 机器在线升级相关信息 -->
    <a-drawer destroyOnClose :title="`${temp.name} 插件版本信息`" placement="right"
      :width="`${this.getCollapsed ? 'calc(100vw - 80px)' : 'calc(100vw - 200px)'}`" :visible="drawerUpgradeVisible"
      @close="() => {
          drawerUpgradeVisible.value = false
        }
        ">
      <!-- 在线升级 -->
      <upgrade v-if="drawerUpgradeVisible" :machineId="temp.id" />
    </a-drawer>
    <!-- 查看机器关联节点 -->
    <a-modal destroyOnClose v-model:visible="viewLinkNode" width="50%" title="关联节点" :footer="null" :maskClosable="false">
      <a-list bordered :data-source="nodeList">
        <a-list-item #renderItem slot-scope="item" style="display: block">
          <a-row>
            <a-col :span="10">节点名称：{{ item.name }}</a-col>
            <a-col :span="10">所属工作空间： {{ item.workspace && item.workspace.name }}</a-col>
            <a-col :span="4">
              <a-button type="link" icon="login" @click="toNode(item.id, item.name, item.workspace && item.workspace.id)">
              </a-button></a-col>
          </a-row>
        </a-list-item>
      </a-list>
    </a-modal>
    <!-- 分发节点白名单 -->
    <a-modal destroyOnClose v-model="whiteConfigVisible" width="50%" title="同步节点白名单" @ok="onSubmitWhitelist"
      :maskClosable="false">
      <a-alert :message="`一键分发同步多个节点的白名单配置,不用挨个配置。配置后会覆盖之前的配置,一般用于节点环境一致的情况`"
        style="margin-top: 10px; margin-bottom: 20px" banner />
      <a-form ref="editWhiteForm" :model="temp" :label-col="{ span: 6 }" :wrapper-col="{ span: 14 }">
        <a-form-item label="模板节点">
          <a-select show-search option-filter-prop="children" @change="(id) => loadWhitelistData(id)"
            placeholder="请选择模板节点" v-model="temp.templateNodeId">
            <a-select-option v-for="item in templateNodeList" :key="item.id" :value="item.id">
              {{ item.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="项目路径" prop="project">
          <a-input v-model="temp.project" type="textarea" :rows="5" style="resize: none"
            placeholder="请输入项目存放路径白名单，回车支持输入多个路径，系统会自动过滤 ../ 路径、不允许输入根路径" />
        </a-form-item>
        <a-form-item label="证书路径" prop="certificate">
          <a-input v-model="temp.certificate" type="textarea" :rows="5" style="resize: none"
            placeholder="请输入证书存放路径白名单，回车支持输入多个路径，系统会自动过滤 ../ 路径、不允许输入根路径" />
        </a-form-item>
        <a-form-item label="Nginx 路径" prop="nginx">
          <a-input v-model="temp.nginx" type="textarea" :rows="5" style="resize: none"
            placeholder="请输入 nginx 存放路径白名单，回车支持输入多个路径，系统会自动过滤 ../ 路径、不允许输入根路径" />
        </a-form-item>
        <a-form-item label="远程下载安全HOST" prop="allowRemoteDownloadHost">
          <a-input v-model="temp.allowRemoteDownloadHost" type="textarea" :rows="5" style="resize: none"
            placeholder="请输入远程下载安全HOST，回车支持输入多个路径，示例 https://www.test.com 等" />
        </a-form-item>
        <a-form-item label="文件后缀" prop="allowEditSuffix">
          <a-input v-model="temp.allowEditSuffix" type="textarea" :rows="5" style="resize: none"
            placeholder="请输入允许编辑文件的后缀及文件编码，不设置编码则默认取系统编码，示例：设置编码：txt@utf-8， 不设置编码：txt" />
        </a-form-item>
      </a-form>
    </a-modal>
    <!-- 分发机器配置 -->
    <a-modal destroyOnClose v-model="nodeConfigVisible" width="50%" title="同步节点配置" @ok="onSubmitWhitelist"
      :maskClosable="false">
      <template #footer>
        <a-space>
          <a-button type="primary" :disabled="!temp.content" @click="onNodeSubmit(false)">保存</a-button>
          <a-button type="primary" :disabled="!temp.content" @click="onNodeSubmit(true)">保存并重启</a-button>
        </a-space>
      </template>
      <a-alert :message="`一键分发同步多个节点的系统配置,不用挨个配置。配置后会覆盖之前的配置,一般用于节点环境一致的情况`" style="margin-top: 10px; margin-bottom: 20px"
        banner />
      <a-form ref="editNodeConfigForm" :model="temp">
        <a-form-item label="模版节点">
          <a-select show-search @change="(id: any) => loadNodeConfig(id)" option-filter-prop="children" placeholder="请选择模版节点"
            v-model="temp.templateNodeId">
            <a-select-option v-for="item in templateNodeList" :key="item.id" :value="item.id">
              {{ item.name }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item class="config-editor" :wrapper-col="{ span: 24 }">
          <code-editor v-model="temp.content" :options="{ mode: 'yaml', tabSize: 2 }"></code-editor>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import {
  machineListData,
  machineListGroup,
  statusMap,
  machineEdit,
  machineDelete,
  machineDistribute,
  machineListNode,
  machineListTemplateNode,
  saveWhitelist,
  saveNodeConfig
} from '@/api/system/assets-machine'
import {
  CHANGE_PAGE,
  COMPUTED_PAGINATION,
  PAGE_DEFAULT_LIST_QUERY,
  PAGE_DEFAULT_SHOW_TOTAL,
  formatDuration,
  parseTime,
  formatPercent2Number,
  getCachePageLimit
} from '@/utils/const'
// import CustomSelect from '@/components/customSelect'
// import { mapGetters } from 'vuex'
import machineInfo from './machine-info.vue'
import { getWorkSpaceListAll } from '@/api/workspace'
// import Upgrade from "@/pages/node/node-layout/system/upgrade.vue";
import upgrade from '@/components/upgrade/index.vue'
import { getWhiteList } from '@/api/node-system'
import { getConfigData } from '@/api/system'
// import codeEditor from '@/components/codeEditor'

import { IPageQuery } from '@/interface/common'

const listQuery = ref<IPageQuery>({ ...PAGE_DEFAULT_LIST_QUERY })
const sizeOptions = ['8', '12', '16', '20', '24']
const list = ref([])
const groupList = ref([])
const loading = ref(true)
const editVisible = ref(false)
const syncToWorkspaceVisible = ref(false)
const temp = ref<any>({})
const rules = {
  name: [{ required: true, message: '请输入机器的名称', trigger: 'blur' }]
}
const drawerVisible = ref(false)
const drawerUpgradeVisible = ref(false)
const workspaceList = ref([])
const viewLinkNode = ref(false)
const nodeList = ref([])
const layoutType =  ref('')
const columns = ref([
  { title: '名称', dataIndex: 'name', width: 150, ellipsis: true, scopedSlots: { customRender: 'name' } },
  { title: '系统名', dataIndex: 'osName', width: 150, ellipsis: true, scopedSlots: { customRender: 'tooltip' } },
  {
    title: '主机名',
    dataIndex: 'hostName',
    width: 150,
    ellipsis: true,
    scopedSlots: { customRender: 'tooltip' }
  },
  {
    title: '节点地址',
    dataIndex: 'jpomUrl',
    width: 150,
    sorter: true,
    ellipsis: true,
    scopedSlots: { customRender: 'tooltip' }
  },
  {
    title: '分组名',
    dataIndex: 'groupName',
    ellipsis: true,
    width: '100px',
    scopedSlots: { customRender: 'tooltip' }
  },
  {
    title: '状态',
    dataIndex: 'status',
    align: 'center',
    width: '100px',
    ellipsis: true,
    scopedSlots: { customRender: 'status' }
  },
  {
    title: '开机时间',
    sorter: true,
    dataIndex: 'osSystemUptime',
    width: 150,
    ellipsis: true,
    scopedSlots: { customRender: 'duration2' }
  },
  {
    title: 'CPU占用',
    sorter: true,
    align: 'center',
    dataIndex: 'osOccupyCpu',
    width: '100px',
    ellipsis: true,
    scopedSlots: { customRender: 'percent2Number' }
  },
  {
    title: '内存占用',
    sorter: true,
    align: 'center',
    dataIndex: 'osOccupyMemory',
    width: '100px',
    ellipsis: true,
    scopedSlots: { customRender: 'percent2Number' }
  },
  {
    title: '硬盘占用',
    sorter: true,
    align: 'center',
    dataIndex: 'osOccupyDisk',
    width: '100px',
    ellipsis: true,
    scopedSlots: { customRender: 'percent2Number' }
  },
  {
    title: '插件版本号',
    dataIndex: 'jpomVersion',
    width: '100px',
    ellipsis: true,
    scopedSlots: { customRender: 'tooltip' }
  },
  {
    title: '模板节点',
    dataIndex: 'templateNode',
    width: '90px',
    align: 'center',
    ellipsis: true,
    customRender: (text: any) => {
      return text ? '是' : '否'
    }
  },
  {
    title: '创建时间',
    dataIndex: 'createTimeMillis',
    ellipsis: true,
    sorter: true,
    customRender: (text: any) => parseTime(text),
    width: '170px'
  },
  {
    title: '修改时间',
    dataIndex: 'modifyTimeMillis',
    customRender: (text: any) => parseTime(text),
    sorter: true,
    width: '170px'
  },
  {
    title: '操作',
    dataIndex: 'operation',
    width: '120px',
    fixed: 'right',
    scopedSlots: { customRender: 'operation' },
    align: 'center'
  }
])
const tableSelections = ref([])
const whiteConfigVisible = ref(false)
const nodeConfigVisible = ref(false)
const templateNodeList = ref<any>([])

const pagination = COMPUTED_PAGINATION(listQuery.value)
const rowSelection = {
  onChange: (selectedRowKeys: any) => {
    tableSelections.value = selectedRowKeys
  },
  selectedRowKeys: tableSelections.value
}
const editNodeForm = ref()
const router = useRouter()
const route = useRoute()

onMounted(() => {
  loadGroupList()
  changeLayout()
})

const loadGroupList = () => {
  machineListGroup().then((res: any) => {
    if (res.data) {
      groupList.value = res.data
    }
  })
}
const changeLayout = () => {
  if (!layoutType.value) {
    const tableLayout = localStorage.getItem('tableLayout')
    // 默认表格
    layoutType.value = tableLayout === 'card' ? 'card' : 'table'
  } else {
    layoutType.value = layoutType.value === 'card' ? 'table' : 'card'
    localStorage.setItem('tableLayout', layoutType.value)
  }
  listQuery.value = { ...listQuery.value, limit: layoutType.value === 'card' ? 8 : getCachePageLimit() }
  getMachineList()
}
const getMachineList = (pointerEvent: any = {}) => {
  loading.value = true
  listQuery.value.page = pointerEvent?.altKey || pointerEvent?.ctrlKey ? 1 : listQuery.value.page
  machineListData(listQuery.value).then((res) => {
    if (res.code === 200) {
      list.value = res.data.result
      listQuery.value.total = res.data.total
    }
    loading.value = false
  })
}
// 分页、排序、筛选变化时触发
const changePage = (pagination: any, filters: any, sorter: any) => {
  listQuery.value = CHANGE_PAGE(listQuery.value, { pagination, sorter })
  getMachineList()
}
const addMachine = () => {
  temp.value = {}
  editVisible.value = true
}
// 修改
const handleEdit = (record: any) => {
  temp.value = Object.assign({}, record)
  delete temp.value.statusMsg
  editVisible.value = true
}
// 提交节点数据
const handleEditOk = () => {
  // 检验表单
  editNodeForm.value.validate((valid: boolean) => {
    if (!valid) {
      return false
    }
    // 提交数据
    machineEdit(temp.value).then((res) => {
      if (res.code === 200) {
        // 成功
        $notification.success({
          message: res.msg
        })
        editNodeForm.value.resetFields()
        editVisible.value = false
        loadGroupList()
        getMachineList()
      }
    })
  })
}
const showMachineInfo = (item: any) => {
  temp.value = { ...item }
  drawerVisible.value = true
}
// 删除机器
const deleteMachineInfo = (item: any) => {
  $confirm({
    title: '系统提示',
    content: '真的要删除机器么？删除会检查数据关联性',
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      // 删除
      machineDelete({
        id: item.id
      }).then((res) => {
        if (res.code === 200) {
          $notification.success({
            message: res.msg
          })
          getMachineList()
        }
      })
    }
  })
}
// 加载工作空间数据
const loadWorkSpaceListAll = () => {
  getWorkSpaceListAll().then((res) => {
    if (res.code === 200) {
      workspaceList.value = res.data
    }
  })
}
// 同步到其他工作情况
const syncToWorkspaceShow = (item: any = {}) => {
  syncToWorkspaceVisible.value = true
  loadWorkSpaceListAll()
  if (item) {
    temp.value = {
      ids: item.id
    }
  }
}
const handleSyncToWorkspace = () => {
  if (!temp.value.workspaceId) {
    $notification.warn({
      message: '请选择工作空间'
    })
    return false
  }
  if (!temp.value.ids) {
    temp.value = { ...temp.value, ids: tableSelections.value.join(',') }
    tableSelections.value = []
  }
  // 同步
  machineDistribute(temp.value).then((res) => {
    if (res.code == 200) {
      $notification.success({
        message: res.msg
      })

      syncToWorkspaceVisible.value = false
      return false
    }
  })
}
// 显示节点版本信息
const showMachineUpgrade = (item: any) => {
  temp.value = { ...item }
  drawerUpgradeVisible.value = true
}
// 查看机器关联的节点
const viewMachineNode = (item: any) => {
  machineListNode({
    id: item.id
  }).then((res) => {
    if (res.code === 200) {
      viewLinkNode.value = true
      nodeList.value = res.data
    }
  })
}
const toNode = (nodeId: string, name: string, wid: string) => {
  const newpage = router.resolve({
    name: 'node_' + nodeId,
    path: '/node/list',
    query: {
      ...route.query,
      nodeId: nodeId,
      pId: 'manage',
      id: 'manageList',
      wid: wid,
      searchNodeName: name
    }
  })
  window.open(newpage.href, '_blank')
}
const syncNodeWhiteConfig = () => {
  if (!tableSelections.value || tableSelections.value.length <= 0) {
    $notification.warn({
      message: '请选择要同步白名单的机器节点'
    })
    return
  }
  machineListTemplateNode().then((res) => {
    if (res.code === 200) {
      if (res.data && res.data.length) {
        whiteConfigVisible.value = true
        templateNodeList.value = res.data
        temp.value = { ...temp.value, templateNodeId: templateNodeList.value[0].id }
        loadWhitelistData(temp.value.templateNodeId)
      } else {
        $notification.warn({
          message: '还没有配置模板节点'
        })
      }
    }
  })
}
// 加载节点白名单分发配置
const loadWhitelistData = (id: string) => {
  getWhiteList({
    machineId: id
  }).then((res) => {
    if (res.code === 200 && res.data) {
      temp.value = Object.assign({}, temp.value, res.data)
    }
  })
}
const onSubmitWhitelist = () => {
  saveWhitelist({
    ...temp.value,
    ids: tableSelections.value.join(',')
  }).then((res) => {
    if (res.code === 200) {
      // 成功
      $notification.success({
        message: res.msg
      })
      tableSelections.value = []
      whiteConfigVisible.value = false
    }
  })
}
const syncNodeConfig = () => {
  if (!tableSelections.value || tableSelections.value.length <= 0) {
    $notification.warn({
      message: '请选择要同步系统配置的机器节点'
    })
    return
  }
  machineListTemplateNode().then((res) => {
    if (res.code === 200) {
      if (res.data && res.data.length) {
        nodeConfigVisible.value = true
        templateNodeList.value = res.data
        temp.value = { ...temp.value, templateNodeId: templateNodeList.value[0].id }
        loadNodeConfig(temp.value.templateNodeId)
      } else {
        $notification.warn({
          message: '还没有配置模板节点'
        })
      }
    }
  })
}

// 修改模版节点
const loadNodeConfig = (id: string) => {
  getConfigData({ machineId: id }).then((res) => {
    if (res.code === 200) {
      temp.value = { ...temp.value, content: res.data.content }
    }
  })
}
// submit
const onNodeSubmit = (restart: boolean) => {
  $confirm({
    title: '系统提示',
    content: restart
      ? '真的要保存当前配置吗？如果配置有误,可能无法启动服务需要手动还原奥！！！ 保存成功后请及时关注重启状态！！'
      : '真的要保存当前配置吗？如果配置有误,可能无法启动服务需要手动还原奥！！！',
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      saveNodeConfig({
        ...temp.value,
        restart: restart,
        ids: tableSelections.value.join(',')
      }).then((res) => {
        if (res.code === 200) {
          // 成功
          $notification.success({
            message: res.msg
          })
          nodeConfigVisible.value = false
          tableSelections.value = []
        }
      })
    }
  })
}
</script>

<style scoped>
.item-info {
  padding: 4px 0;
}

.item-info .title {
  /* display: inline; */
  /* font-weight: bold; */
}

.item-info .content {
  /* display: inline; */
}

.config-editor {
  height: 40vh;
  width: 100%;
  overflow-y: scroll;
  border: 1px solid #d9d9d9;
}
</style>
