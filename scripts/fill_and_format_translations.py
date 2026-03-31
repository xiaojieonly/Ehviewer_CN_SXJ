#!/usr/bin/env python3
"""
脚本用于根据基础strings.xml文件补全其他语言的XML文件并格式化XML文档
"""

import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path
import argparse
from typing import Dict, List, Tuple


class StringsXMLProcessor:
    def __init__(self, base_file: str):
        self.base_file = Path(base_file)
        self.base_strings = {}
        self.base_comments = {}
        
    def parse_base_file(self):
        """解析基础strings.xml文件"""
        tree = ET.parse(self.base_file)
        root = tree.getroot()
        
        for elem in root:
            if elem.tag == 'string':
                name = elem.get('name')
                if name:
                    self.base_strings[name] = elem.text or ''
                    # 保存注释
                    if elem.tail and elem.tail.strip():
                        self.base_comments[name] = elem.tail.strip()
            elif elem.tag == 'plurals':
                name = elem.get('name')
                if name:
                    self.base_strings[f'plurals_{name}'] = elem
            elif elem.tag == 'comment':
                # 处理XML注释
                pass
                
    def get_all_language_dirs(self):
        """获取所有语言目录（排除配置特定的覆盖文件）"""
        res_dir = self.base_file.parent.parent
        language_dirs = []
        
        # 配置特定的覆盖文件模式
        config_patterns = [
            'values-land',
            'values-large',
            'values-night',
            'values-sw',
            'values-v',
        ]
        
        for item in res_dir.iterdir():
            if item.is_dir() and item.name.startswith('values-') and item.name != 'values':
                # 检查是否是配置特定的覆盖文件
                is_config_override = any(item.name.startswith(pattern) for pattern in config_patterns)
                if not is_config_override:
                    language_dirs.append(item)
                
        return language_dirs
    
    def get_all_config_dirs(self):
        """获取所有配置特定的覆盖文件目录"""
        res_dir = self.base_file.parent.parent
        config_dirs = []
        
        # 配置特定的覆盖文件模式
        config_patterns = [
            'values-land',
            'values-large',
            'values-night',
            'values-sw',
            'values-v',
        ]
        
        for item in res_dir.iterdir():
            if item.is_dir() and item.name.startswith('values-') and item.name != 'values':
                # 检查是否是配置特定的覆盖文件
                is_config_override = any(item.name.startswith(pattern) for pattern in config_patterns)
                if is_config_override:
                    config_dirs.append(item)
                
        return config_dirs
    
    def parse_language_file(self, lang_file: Path) -> Tuple[Dict, List]:
        """解析语言文件，返回字符串字典和注释列表"""
        strings = {}
        comments = []
        
        if not lang_file.exists():
            return strings, comments
            
        try:
            tree = ET.parse(lang_file)
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
                elif elem.tag == 'comment':
                    comments.append(elem.text)
                    
        except ET.ParseError:
            print(f"警告: 无法解析 {lang_file}")
            
        return strings, comments
    
    def create_missing_strings(self, lang_file: Path, lang_strings: Dict):
        """创建缺失的字符串"""
        missing_count = 0
        
        # 读取文件内容
        with open(lang_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # 找到<resources>标签的位置
        resources_match = re.search(r'<resources>', content)
        if not resources_match:
            print(f"警告: 在 {lang_file} 中找不到 <resources> 标签")
            return 0
            
        insert_pos = resources_match.end()
        
        # 准备要插入的新字符串
        new_strings = []
        
        for name, value in self.base_strings.items():
            if name not in lang_strings:
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
                        new_strings.append(self._element_to_string(new_elem))
                else:
                    # 处理普通字符串
                    new_strings.append(f'    <string name="{name}">{value}</string>')
                missing_count += 1
                
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
                with open(lang_file, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                    
        return missing_count
    
    def format_xml_file(self, file_path: Path):
        """格式化XML文件"""
        try:
            tree = ET.parse(file_path)
            root = tree.getroot()
            
            # 重新格式化
            self._indent_xml(root)
            
            # 写入格式化后的内容
            tree.write(file_path, encoding='utf-8', xml_declaration=True)
            
        except ET.ParseError:
            print(f"警告: 无法格式化 {file_path}")
    
    def _element_to_string(self, element: ET.Element) -> str:
        """将XML元素转换为字符串"""
        return ET.tostring(element, encoding='unicode')
    
    def format_xml_file(self, file_path: Path):
        """格式化XML文件"""
        try:
            tree = ET.parse(file_path)
            root = tree.getroot()
            
            # 重新格式化
            self._indent_xml(root)
            
            # 写入格式化后的内容
            tree.write(file_path, encoding='utf-8', xml_declaration=True)
            
        except ET.ParseError:
            print(f"警告: 无法格式化 {file_path}")
    
    def _indent_xml(self, elem, level=0):
        """为XML元素添加缩进"""
        i = "\n" + level * "    "
        if len(elem):
            if not elem.text or not elem.text.strip():
                elem.text = i + "    "
            if not elem.tail or not elem.tail.strip():
                elem.tail = i
            for child in elem:
                self._indent_xml(child, level + 1)
            if not child.tail or not child.tail.strip():
                child.tail = i
        else:
            if level and (not elem.tail or not elem.tail.strip()):
                elem.tail = i
    
    def find_missing_in_base(self):
        """找出基础文件中缺失的字符串（从其他语言文件中）"""
        language_dirs = self.get_all_language_dirs()
        all_strings = set(self.base_strings.keys())
        
        for lang_dir in language_dirs:
            lang_file = lang_dir / 'strings.xml'
            if lang_file.exists():
                lang_strings, _ = self.parse_language_file(lang_file)
                all_strings.update(lang_strings.keys())
        
        # 找出基础文件中没有的字符串
        missing_in_base = all_strings - set(self.base_strings.keys())
        return missing_in_base
    
    def add_missing_to_base(self, missing_strings, dry_run=False):
        """将缺失的字符串添加到基础文件"""
        if not missing_strings:
            return 0
            
        print(f"\n发现 {len(missing_strings)} 个字符串在基础文件中缺失:")
        for name in sorted(missing_strings):
            print(f"  {name}")
        
        if dry_run:
            print("\n(模拟运行，不实际修改基础文件)")
            return len(missing_strings)
        
        # 读取基础文件内容
        with open(self.base_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 找到</resources>标签的位置
        resources_end_match = re.search(r'</resources>', content)
        if not resources_end_match:
            print("警告: 在基础文件中找不到 </resources> 标签")
            return 0
        
        # 准备要插入的新字符串
        new_strings = []
        for name in sorted(missing_strings):
            # 从其他语言文件中获取值
            value = None
            for lang_dir in self.get_all_language_dirs():
                lang_file = lang_dir / 'strings.xml'
                if lang_file.exists():
                    lang_strings, _ = self.parse_language_file(lang_file)
                    if name in lang_strings:
                        value = lang_strings[name]
                        break
            
            if value is not None:
                if name.startswith('plurals_'):
                    # 处理复数形式
                    plurals_name = name[8:]
                    new_strings.append(f'    <plurals name="{plurals_name}">')
                    new_strings.append('        <!-- TODO: Add plural items -->')
                    new_strings.append('    </plurals>')
                else:
                    # 处理普通字符串
                    new_strings.append(f'    <string name="{name}">{value}</string>')
        
        if new_strings:
            # 在</resources>之前插入新字符串
            insert_pos = resources_end_match.start()
            new_content = (content[:insert_pos] + '\n' + 
                          '\n'.join(new_strings) + '\n' + 
                          content[insert_pos:])
            
            # 写入文件
            with open(self.base_file, 'w', encoding='utf-8') as f:
                f.write(new_content)
            
            # 更新基础字符串字典
            for name in missing_strings:
                if not name.startswith('plurals_'):
                    self.base_strings[name] = ''
        
        return len(new_strings)
    
    def process_all_languages(self, dry_run=False):
        """处理所有语言文件"""
        language_dirs = self.get_all_language_dirs()
        config_dirs = self.get_all_config_dirs()
        
        print(f"找到 {len(language_dirs)} 个语言目录")
        print(f"找到 {len(config_dirs)} 个配置覆盖目录")
        
        # 首先检查基础文件中是否有缺失的字符串
        missing_in_base = self.find_missing_in_base()
        if missing_in_base:
            self.add_missing_to_base(missing_in_base, dry_run)
        
        # 处理语言文件
        for lang_dir in language_dirs:
            lang_file = lang_dir / 'strings.xml'
            print(f"\n处理语言文件: {lang_dir.name}")
            
            if not lang_file.exists():
                print(f"  文件不存在，跳过")
                continue
                
            lang_strings, _ = self.parse_language_file(lang_file)
            missing_count = self.create_missing_strings(lang_file, lang_strings)
            
            if missing_count > 0:
                print(f"  添加了 {missing_count} 个缺失的字符串")
                
                if not dry_run:
                    self.format_xml_file(lang_file)
                    print(f"  已格式化 {lang_file.name}")
            else:
                print(f"  没有缺失的字符串")
                
                if not dry_run:
                    self.format_xml_file(lang_file)
                    print(f"  已格式化 {lang_file.name}")
        
        # 处理配置覆盖文件
        for config_dir in config_dirs:
            config_file = config_dir / 'strings.xml'
            print(f"\n处理配置覆盖文件: {config_dir.name}")
            
            if not config_file.exists():
                print(f"  文件不存在，跳过")
                continue
                
            # 对于配置覆盖文件，只格式化，不添加缺失的字符串
            if not dry_run:
                self.format_xml_file(config_file)
                print(f"  已格式化 {config_file.name}")
            else:
                print(f"  (模拟运行，不格式化)")


def main():
    parser = argparse.ArgumentParser(description='补全和格式化翻译XML文件')
    parser.add_argument('--base', required=True, help='基础strings.xml文件路径')
    parser.add_argument('--dry-run', action='store_true', help='只显示将要进行的更改，不实际修改文件')
    
    args = parser.parse_args()
    
    if not os.path.exists(args.base):
        print(f"错误: 基础文件不存在: {args.base}")
        return 1
        
    processor = StringsXMLProcessor(args.base)
    processor.parse_base_file()
    
    print(f"基础文件: {args.base}")
    print(f"找到 {len(processor.base_strings)} 个基础字符串")
    
    processor.process_all_languages(dry_run=args.dry_run)
    
    if args.dry_run:
        print("\n注意: 这是模拟运行，没有实际修改文件")
    
    return 0


if __name__ == '__main__':
    exit(main())