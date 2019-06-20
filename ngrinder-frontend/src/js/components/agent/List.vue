 <template>
    <div class="container">
        <fieldSet>
            <legend class="header" v-text="i18n('agent.list.title')"></legend>
        </fieldSet>
        <select v-if="ngrinder.config.clustered" id="regions" class="pull-right form-control change-region"
                name="regions" v-model="region" @change="changeRegion">
            <option value="all">All</option>
            <option v-for="region in data.regions" :value="region" v-text="region"></option>
        </select>
        <div class="well search-bar">
            <button class="btn btn-success" id="update_agent_button" @click="update">
                <i class="icon-arrow-up"></i><span v-text="i18n('agent.list.update')"></span>
            </button>
            <button class="btn" id="cleanup_agent_button" @click="cleanUp">
                <i class="icon-trash"></i><span v-text="i18n('common.button.cleanup')"></span>
            </button>
            <button class="btn" id="stop_agent_button" @click="stopAgents">
                <i class="icon-stop"></i><span v-text="i18n('common.button.stop')"></span>
            </button>

            <div class="input-prepend pull-right">
                <span class="add-on" style="cursor:default" v-text="i18n('agent.list.download')"></span>
                <span class="input-xlarge uneditable-input span6" style="cursor: text">
                <template v-if="downloadLink">
                    <a :href="downloadLink" v-html="downloadLink"></a>
                </template>
                <template v-else>
                    Please select the region in advance to download agent.
                </template>
			</span>
            </div>
        </div>
        <table class="table table-striped table-bordered ellipsis" id="agent_table">
            <colgroup>
                <col width="30">
                <col width="80">
                <col width="130">
                <col width="60">
                <col width="*">
                <col width="100">
                <col width="150">
                <col width="160">
            </colgroup>
            <thead>
            <tr>
                <th class="no-click nothing"><input type="checkbox" class="checkbox" v-model="selectAll"
                                                    @change="changeSelectAll"></th>
                <th v-text="i18n('agent.list.state')"></th>
                <th v-text="i18n('agent.list.IPAndDns')"></th>
                <th class="no-click" v-text="i18n('agent.list.port')"></th>
                <th class="ellipsis" v-text="i18n('agent.list.name')"></th>
                <th v-text="i18n('agent.list.version')"></th>
                <th v-text="i18n('agent.list.region')"></th>
                <th class="no-click" v-text="i18n('agent.list.approved')"></th>
            </tr>
            </thead>
            <tbody>
            <template v-if="agents.length > 0">
                <tr v-for="agent in agents">
                    <td class="center">
                        <input type="checkbox" class="agent-state checkbox" v-model="selectedAgents" :value="agent.id">
                    </td>
                    <td class="center" :id="'row_' +agent.id ">
                        <div class="ball" :id="'ball_' + agent.id " data-html="true" rel="popover">
                            <img class="status" :src="`/img/ball/${agent.state.iconName}`"/>
                        </div>
                    </td>
                    <td>
                        <div class="ellipsis" :title="agent.ip">
                            <a :href="`/agent/${agent.id}`" target="_self" :value="agent.ip" v-text="agent.ip"></a>
                        </div>
                    </td>
                    <td id="'port_'+agent.id" v-text="agent.port"></td>
                    <td class="ellipsis agent-name" :title="agent.hostName" v-text="agent.hostName"></td>
                    <td class="ellipsis" v-text="agent.version || 'Prior to 3.3'"></td>
                    <td class="ellipsis" :title="agent.region" v-text="agent.region"></td>
                    <td>
                        <div class="btn-group" data-toggle="buttons-radio">
                            <button type="button" class="btn btn-xs btn-primary disapproved"
                                    :class="{ active: !agent.approved }"
                                    :sid="agent.id" v-text="i18n('agent.list.disapproved')" @click="disapprove(agent)">
                            </button>
                            <button type="button" class="btn btn-xs btn-primary approved"
                                    :class="{ active: agent.approved }"
                                    :sid="agent.id" v-text="i18n('agent.list.approved')" @click="approve(agent)">
                            </button>
                        </div>
                    </td>
                </tr>
            </template>
            <tr v-else>
                <td colspan="8" class="center" v-text="i18n('common.message.noData')"></td>
            </tr>
            </tbody>
        </table>
    </div>
</template>

<script>
    import Component from 'vue-class-component';
    import Base from '../Base.vue';

    @Component({
        name: 'agentList',
    })
    export default class AgentList extends Base {
        regions = [];
        region = '';

        agents = [];

        downloadLink = '';

        page = {
            number: 1,
            totalPages: 1,
            size: 10,
        };

        selectAll = false;
        selectedAgents = [];

        updateStatesTimer = null;

        created() {
            this.$http.get('/agent/api/regions').then(res => this.regions = res.data);
            this.$http.get('/agent/api/download_link').then(res => this.downloadLink = res.data);

            this.updateStates();
        }

        beforeDestroy() {
            clearTimeout(this.updateStatesTimer);
        }

        updateStates() {
            this.$http.get('/agent/api/list', { params: { region: this.region } })
                .then(res => this.agents = res.data)
                .finally(() => this.updateStatesTimer = setTimeout(this.updateStates, 2000));
        }

        changeRegion(event) {
            // change region
            this.region = event.target.value;

            clearTimeout(this.updateStatesTimer);
            this.updateStates();
        }

        update() {
            // update
        }

        cleanUp() {
            // cleanUp
        }

        stopAgents() {
            // stop
        }

        approve(agent) {
            // approve
        }

        disapprove(agent) {
            // disapprove
        }

        changeSelectAll(event) {
            if (event.target.checked) {
                this.selectedAgents = this.agents.map(agent => agent.id);
            } else {
                this.selectedAgents = [];
            }
        }
    }
</script>

<style lang="less" scoped>
    .change-region {
        margin-top: -53px;
        width: 150px;
    }
</style>
