from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[2]
zh={'check':'检查背包','display':'显示设置','columns_icons':'栏目：图标','columns_expanded':'栏目：展开','details_overlay':'详情：覆盖','details_sidebar':'详情：侧栏','edit_on':'编辑模式','edit_off':'退出编辑','close_detail':'关闭详情','chapter_choices':'选择栏目及章节','edit_target':'编辑：%s','complete_scope':'直接完成（包含必要前置）：%s项','reset_scope':'重置及受影响后续：%s项','direct_complete':'直接完成','direct_reset':'重置为未解锁','cancel':'取消','checked_none':'未发现可补全的记录','checked':'检查完成，新完成 %s 项发现','editing_on':'已开启编辑模式','editing_off':'已退出编辑模式','edit_completed':'已直接完成 %s 项研究','edit_reset':'已重置 %s 项研究','denied':'权限不足，操作已拒绝','stale':'状态已变化，请重新操作','editing_banner':'编辑模式 · 仅修改自己的进度 · 显示隐藏节点不会自动解锁'}
en={'check':'Check inventory','display':'Display settings','columns_icons':'Columns: Icons','columns_expanded':'Columns: Expanded','details_overlay':'Details: Overlay','details_sidebar':'Details: Sidebar','edit_on':'Edit mode','edit_off':'Exit editing','close_detail':'Close details','chapter_choices':'Choose a category and chapter','edit_target':'Edit: %s','complete_scope':'Complete, including prerequisites: %s','reset_scope':'Reset, including affected descendants: %s','direct_complete':'Complete directly','direct_reset':'Reset to locked','cancel':'Cancel','checked_none':'No discoveries to complete','checked':'Check complete: %s new discoveries','editing_on':'Edit mode enabled','editing_off':'Edit mode disabled','edit_completed':'Completed %s research entries','edit_reset':'Reset %s research entries','denied':'Permission denied','stale':'State changed; try again','editing_banner':'EDIT MODE · Your progress only · Revealing nodes does not unlock them'}
for locale,values in [('zh_cn',zh),('en_us',en)]:
    target=ROOT/f'src/main/resources/assets/white_lily/lang/{locale}.json'
    original=target.read_bytes().decode('utf-8');current=json.loads(original)
    additions={'screen.white_lily.research.'+key:value for key,value in values.items()}
    conflicts=[key for key,value in additions.items() if key in current and current[key]!=value]
    if conflicts:raise ValueError(conflicts)
    pending={key:value for key,value in additions.items() if key not in current}
    if pending:
        newline='\r\n' if '\r\n' in original else '\n'
        content=original.rstrip().removesuffix('}').rstrip()+','+newline+json.dumps(pending,ensure_ascii=False,indent=2)[2:-2].replace('\n',newline)+newline+'}'+newline
        target.write_bytes(content.encode('utf-8'))
    (Path(__file__).parent/f'archive_{locale}.json').write_text(json.dumps(additions,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(locale,len(pending))
