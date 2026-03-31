#!/usr/bin/env python3
"""
脚本用于将基础strings.xml中的字符串添加到其他语言文件中
"""

import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path
import argparse


def parse_strings_file(file_path):
    """解析strings.xml文件，返回字符串字典"""
    strings = {}
    
    if not file_path.exists():
        return strings
        
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        
        for elem in root:
            if elem.tag == 'string':
                name = elem.get('name')
                if name:
                    strings[name] = elem.text or ''
            elif elem.tag == 'plurals':
                name = elem.get('name')
                if name:
                    strings[f'plurals_{name}'] = elem
                    
    except ET.ParseError:
        print(f"警告: 无法解析 {file_path}")
        
    return strings


def add_missing_strings_to_file(base_strings, target_file, dry_run=False):
    """将缺失的字符串添加到目标文件"""
    target_strings = parse_strings_file(target_file)
    
    # 找出缺失的字符串
    missing_strings = []
    for name, value in base_strings.items():
        if name not in target_strings:
            missing_strings.append((name, value))
    
    if not missing_strings:
        print(f"  没有缺失的字符串")
        return 0
    
    print(f"  发现 {len(missing_strings)} 个缺失的字符串")
    
    if dry_run:
        return len(missing_strings)
    
    # 读取文件内容
    with open(target_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 找到<resources>标签的位置
    resources_match = re.search(r'<resources>', content)
    if not resources_match:
        print(f"  警告: 在文件中找不到 <resources> 标签")
        return 0
    
    insert_pos = resources_match.end()
    
    # 准备要插入的新字符串
    new_strings = []
    for name, value in missing_strings:
        if name.startswith('plurals_'):
            # 处理复数形式
            plurals_name = name[8:]  # 移除 'plurals_' 前缀
            base_elem = value
            if isinstance(base_elem, ET.Element):
                # 创建新的复数元素
                new_elem = ET.Element('plurals', name=plurals_name)
                for item in base_elem:
                    new_item = ET.SubElement(new_elem, 'item', quantity=item.get('quantity'))
                    new_item.text = item.text
                new_strings.append(ET.tostring(new_elem, encoding='unicode'))
        else:
            # 处理普通字符串
            new_strings.append(f'    <string name="{name}">{value}</string>')
    
    if new_strings:
        # 在</resources>之前插入新字符串
        resources_end_match = re.search(r'</resources>', content)
        if resources_end_match:
            insert_pos_end = resources_end_match.start()
            # 在插入位置添加新字符串
            new_content = (content[:insert_pos] + '\n' + 
                          '\n'.join(new_strings) + '\n' + 
                          content[insert_pos:])
            
            # 写入文件
            with open(target_file, 'w', encoding='utf-8') as f:
                f.write(new_content)
    
    return len(new_strings)


def main():
    parser = argparse.ArgumentParser(description='将基础strings.xml中的字符串添加到其他语言文件')
    parser.add_argument('--base', required=True, help='基础strings.xml文件路径')
    parser.add_argument('--dry-run', action='store_true', help='只显示将要进行的更改，不实际修改文件')
    
    args = parser.parse_args()
    
    if not os.path.exists(args.base):
        print(f"错误: 基础文件不存在: {args.base}")
        return 1
    
    base_file = Path(args.base)
    base_strings = parse_strings_file(base_file)
    
    print(f"基础文件: {args.base}")
    print(f"找到 {len(base_strings)} 个基础字符串")
    
    # 获取所有语言目录
    res_dir = base_file.parent.parent
    language_dirs = []
    
    for item in res_dir.iterdir():
        if item.is_dir() and item.name.startswith('values-') and item.name != 'values':
            # 排除配置特定的覆盖文件
            config_patterns = ['values-land', 'values-large', 'values-night', 'values-sw', 'values-v']
            is_config_override = any(item.name.startswith(pattern) for pattern in config_patterns)
            if not is_config_override:
                language_dirs.append(item)
    
    print(f"找到 {len(language_dirs)} 个语言目录")
    
    total_added = 0
    for lang_dir in language_dirs:
        lang_file = lang_dir / 'strings.xml'
        print(f"\n处理: {lang_dir.name}")
        
        if not lang_file.exists():
            print(f"  文件不存在，跳过")
            continue
        
        added_count = add_missing_strings_to_file(base_strings, lang_file, args.dry_run)
        total_added += added_count
        
        if added_count > 0:
            print(f"  添加了 {added_count} 个缺失的字符串")
    
    print(f"\n总计添加了 {total_added} 个字符串")
    
    if args.dry_run:
        print("\n注意: 这是模拟运行，没有实际修改文件")
    
    return 0


if __name__ == '__main__':
    exit(main())